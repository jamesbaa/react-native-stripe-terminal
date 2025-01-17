package com.reactnative_stripeterminal;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.stripe.stripeterminal.external.callable.Callback;
import com.stripe.stripeterminal.external.callable.Cancelable;
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback;
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider;
import com.stripe.stripeterminal.external.callable.DiscoveryListener;
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback;
import com.stripe.stripeterminal.external.callable.ReaderCallback;
import com.stripe.stripeterminal.external.callable.ReaderSoftwareUpdateCallback;
import com.stripe.stripeterminal.external.callable.TerminalListener;
import com.stripe.stripeterminal.log.LogLevel;
import com.stripe.stripeterminal.external.models.ConnectionConfiguration.InternetConnectionConfiguration;
import com.stripe.stripeterminal.external.models.ConnectionStatus;
import com.stripe.stripeterminal.external.models.Cart;
import com.stripe.stripeterminal.external.models.CartLineItem;
import com.stripe.stripeterminal.external.models.ConnectionTokenException;
import com.stripe.stripeterminal.external.models.DiscoveryMethod;
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration;
import com.stripe.stripeterminal.external.models.PaymentIntent;
import com.stripe.stripeterminal.external.models.PaymentIntentParameters;
import com.stripe.stripeterminal.external.models.PaymentStatus;
import com.stripe.stripeterminal.external.models.Reader;
import com.stripe.stripeterminal.external.models.ReaderDisplayMessage;
import com.stripe.stripeterminal.external.models.ReaderEvent;
import com.stripe.stripeterminal.external.models.ReaderInputOptions;
import com.stripe.stripeterminal.external.models.ReaderSoftwareUpdate;
import com.stripe.stripeterminal.external.models.TerminalException;
import com.stripe.stripeterminal.Terminal;
import com.stripe.stripeterminal.external.callable.BluetoothReaderListener;
import com.stripe.stripeterminal.external.models.ConnectionConfiguration.BluetoothConnectionConfiguration;

import java.sql.Wrapper;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.ArrayList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.reactnative_stripeterminal.Constants.*;

public class RNStripeTerminalModule extends ReactContextBaseJavaModule implements TerminalListener, ConnectionTokenProvider, BluetoothReaderListener,DiscoveryListener {
    final static String TAG = RNStripeTerminalModule.class.getSimpleName();
    final static String moduleName = "RNStripeTerminal";
    Cancelable pendingDiscoverReaders = null;
    Cancelable pendingCreatePaymentIntent = null;
    PaymentIntent lastPaymentIntent = null;
    ReaderEvent lastReaderEvent=ReaderEvent.CARD_REMOVED;
    ConnectionTokenCallback pendingConnectionTokenCallback = null;
    String lastCurrency = null;
    List<? extends Reader> discoveredReadersList = null;
    ReaderSoftwareUpdate readerSoftwareUpdate;
    Cancelable pendingInstallUpdate = null;

    public RNStripeTerminalModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    ReactContext getContext(){
        return getReactApplicationContext();
    }

    @Override
    public String getName() {
        return moduleName;
    }

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        return constants;
    }

    public void sendEventWithName(String eventName, WritableMap eventData){
        getContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, eventData);
    }

    public void sendEventWithName(String eventName, Object eventData){
        getContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, eventData);
    }

    public void sendEventWithName(String eventName, WritableArray eventData){
        getContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, eventData);
    }

    WritableMap serializeUpdate(ReaderSoftwareUpdate readerSoftwareUpdate){
        WritableMap writableMap = Arguments.createMap();
        WritableMap updateMap = Arguments.createMap();

        if(readerSoftwareUpdate!=null){
            ReaderSoftwareUpdate.UpdateTimeEstimate updateTimeEstimate= readerSoftwareUpdate.getTimeEstimate();
            updateMap.putString(ESTIMATED_UPDATE_TIME,updateTimeEstimate.getDescription());
            updateMap.putString(DEVICE_SOFTWARE_VERSION,readerSoftwareUpdate.getVersion());
            writableMap.putMap(UPDATE,updateMap);
        }

        return writableMap;
    }

    WritableMap serializeReader(Reader reader) {
        WritableMap writableMap = Arguments.createMap();
        if(reader!=null) {
            double batteryLevel = 0;
            if(reader.getBatteryLevel()!=null)
                batteryLevel = (double) reader.getBatteryLevel();
            writableMap.putDouble(BATTERY_LEVEL, batteryLevel);

            int readerType = 0;
            if(reader.getDeviceType()!=null)
                readerType = reader.getDeviceType().ordinal();
            writableMap.putInt(DEVICE_TYPE, readerType);

            String serial = "";

            if(reader.getSerialNumber()!=null)
                serial = reader.getSerialNumber();
            writableMap.putString(SERIAL_NUMBER, serial);

            String softwareVersion = "";
            if(reader.getSoftwareVersion()!=null)
                softwareVersion = reader.getSoftwareVersion();
            writableMap.putString(DEVICE_SOFTWARE_VERSION, softwareVersion);
        }
        return writableMap;
    }

    WritableMap serializePaymentIntent(PaymentIntent paymentIntent,String currency){
        WritableMap paymentIntentMap = Arguments.createMap();
        paymentIntentMap.putString(STRIPE_ID,paymentIntent.getId());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZ");
        paymentIntentMap.putString(CREATED,simpleDateFormat.format(new Date(paymentIntent.getCreated())));
        paymentIntentMap.putInt(STATUS,paymentIntent.getStatus().ordinal());
        paymentIntentMap.putInt(AMOUNT,(int)paymentIntent.getAmount());
        paymentIntentMap.putString(CURRENCY,currency);
        WritableMap metaDataMap = Arguments.createMap();
        if(paymentIntent.getMetadata()!=null){
            for(String key:paymentIntent.getMetadata().keySet()){
                metaDataMap.putString(key,String.valueOf(paymentIntent.getMetadata().get(key)));
            }
        }
        paymentIntentMap.putMap(METADATA,metaDataMap);
        return paymentIntentMap;
    }

    @ReactMethod
    public void discoverReaders(int method, int simulated, String locationId ) {
        boolean isSimulated = simulated == 0?false:true;
        try {
            DiscoveryConfiguration discoveryConfiguration = new DiscoveryConfiguration(0, DiscoveryMethod.INTERNET, isSimulated, locationId);
            Callback statusCallback = new Callback() {
                @Override
                public void onSuccess() {
                    pendingDiscoverReaders = null;
                    WritableMap readerCompletionResponse = Arguments.createMap();
                    sendEventWithName(EVENT_READER_DISCOVERY_COMPLETION,readerCompletionResponse);
                }

                @Override
                public void onFailure(@Nonnull TerminalException e) {
                    pendingDiscoverReaders = null;
                    WritableMap errorMap = Arguments.createMap();
                    errorMap.putString(ERROR, e.getErrorMessage());
                    sendEventWithName(EVENT_READER_DISCOVERY_COMPLETION,errorMap);
                }
            };

            abortDiscoverReaders();
            pendingDiscoverReaders = Terminal.getInstance().discoverReaders(discoveryConfiguration, this, statusCallback);

        }catch (Exception e){
            e.printStackTrace();

            if(e.getMessage()!=null) {
                WritableMap writableMap = Arguments.createMap();
                writableMap.putString(ERROR,e.getMessage());
                sendEventWithName(EVENT_READER_DISCOVERY_COMPLETION, writableMap);
            }
        }
    }

    @ReactMethod
    public void initialize(com.facebook.react.bridge.Callback callback) {
        try {
            //Check if stripe is initialized
            Terminal.getInstance();

            WritableMap writableMap = Arguments.createMap();
            writableMap.putBoolean("isInitialized", true);
            callback.invoke(writableMap);
            return;
        }catch (IllegalStateException e){ }

        pendingConnectionTokenCallback = null;
        abortDiscoverReaders();
        abortCreatePayment();
        abortInstallUpdate();

        LogLevel logLevel = LogLevel.VERBOSE;
        ConnectionTokenProvider tokenProvider = this;
        TerminalListener terminalListener = this;
        String err = "";
        boolean isInit =false;
        try {
            Terminal.initTerminal(getContext().getApplicationContext(), logLevel, tokenProvider, terminalListener);
            lastReaderEvent = ReaderEvent.CARD_REMOVED;
            isInit = true;
        } catch (TerminalException e) {
            e.printStackTrace();
            err = e.getErrorMessage();
            isInit = false;
        } catch (IllegalStateException ex){
            ex.printStackTrace();
            err=  ex.getMessage();
            isInit = true;
        }

        WritableMap writableMap = Arguments.createMap();
        writableMap.putBoolean("isInitialized", isInit);

        if(!isInit) {
            writableMap.putString(ERROR, err);
        }

        callback.invoke(writableMap);
    }

    @ReactMethod
    public void setConnectionToken(String token,String errorMsg){
        if(pendingConnectionTokenCallback!=null){
            if(errorMsg!=null && !errorMsg.trim().isEmpty()){
                pendingConnectionTokenCallback.onFailure(new ConnectionTokenException(errorMsg));
            }else{
                pendingConnectionTokenCallback.onSuccess(token);
            }
        }

        pendingConnectionTokenCallback = null;
    }

    @ReactMethod
    public void createPayment(final String paymentIntent) {
        PaymentIntentCallback paymentIntentCallback = new PaymentIntentCallback() {
            @Override
            public void onSuccess(@Nonnull final PaymentIntent paymentIntent) {
                pendingCreatePaymentIntent = Terminal.getInstance().collectPaymentMethod(paymentIntent
                        , new PaymentIntentCallback() {
                            @Override
                            public void onSuccess(@Nonnull final PaymentIntent collectedIntent) {
                                pendingCreatePaymentIntent = null;
                                Terminal.getInstance().processPayment(collectedIntent, new PaymentIntentCallback() {
                                    @Override
                                    public void onSuccess(@Nonnull PaymentIntent confirmedIntent) {
                                        WritableMap intentMap = Arguments.createMap();
                                        String currency = "";
                                        intentMap.putMap(INTENT, serializePaymentIntent(confirmedIntent, currency));
                                        sendEventWithName(EVENT_PAYMENT_CREATION, intentMap);
                                    }

                                    @Override
                                    public void onFailure(@Nonnull TerminalException e) {
                                        WritableMap errorMap = Arguments.createMap();
                                        errorMap.putString(ERROR, e.getErrorMessage());
                                        errorMap.putInt(CODE, e.getErrorCode().ordinal());
                                        String currency = "";
                                        errorMap.putMap(INTENT, serializePaymentIntent(collectedIntent, currency));
                                        sendEventWithName(EVENT_PAYMENT_CREATION, errorMap);
                                    }
                                });
                            }

                            @Override
                            public void onFailure(@Nonnull TerminalException e) {
                                pendingCreatePaymentIntent = null;
                                WritableMap collectionErrorMap = Arguments.createMap();
                                collectionErrorMap.putString(ERROR, e.getErrorMessage());
                                collectionErrorMap.putInt(CODE, e.getErrorCode().ordinal());
                                String currency = "";
                                collectionErrorMap.putMap(INTENT, serializePaymentIntent(paymentIntent, currency));
                                sendEventWithName(EVENT_PAYMENT_CREATION, collectionErrorMap);
                            }
                        });
            }

            @Override
            public void onFailure(@Nonnull TerminalException e) {
                WritableMap paymentCreationMap = Arguments.createMap();
                paymentCreationMap.putString(ERROR, e.getErrorMessage());
                paymentCreationMap.putInt(CODE, e.getErrorCode().ordinal());
                sendEventWithName(EVENT_PAYMENT_CREATION, paymentCreationMap);
            }
        };

     
        Terminal.getInstance().retrievePaymentIntent(paymentIntent, paymentIntentCallback);
     
    }

    @ReactMethod
    public void retrievePaymentIntent(String clientSecret){
        if(clientSecret!=null) {
            Terminal.getInstance().retrievePaymentIntent(clientSecret, new PaymentIntentCallback() {
                @Override
                public void onSuccess(@Nonnull PaymentIntent paymentIntent) {
                    lastPaymentIntent  = paymentIntent;
                    WritableMap paymentRetrieveRespMap = Arguments.createMap();
                    paymentRetrieveRespMap.putMap(INTENT,serializePaymentIntent(paymentIntent,"")); //No currency for android
                    sendEventWithName(EVENT_PAYMENT_INTENT_RETRIEVAL, paymentRetrieveRespMap);
                }

                @Override
                public void onFailure(@Nonnull TerminalException e) {
                    lastPaymentIntent = null;
                    WritableMap paymentRetrieveRespMap = Arguments.createMap();
                    paymentRetrieveRespMap.putString(ERROR,e.getErrorMessage());
                    sendEventWithName(EVENT_PAYMENT_INTENT_RETRIEVAL, paymentRetrieveRespMap);
                }
            });
        }else{
            WritableMap paymentRetrieveRespMap = Arguments.createMap();
            paymentRetrieveRespMap.putString(ERROR,"Client secret cannot be null");
            sendEventWithName(EVENT_PAYMENT_INTENT_RETRIEVAL, paymentRetrieveRespMap);
        }
    }

    @ReactMethod
    public void cancelPaymentIntent(){
        Terminal.getInstance().cancelPaymentIntent(lastPaymentIntent, new PaymentIntentCallback() {
            @Override
            public void onSuccess(@Nonnull PaymentIntent paymentIntent) {
                WritableMap paymentIntentCancelMap = Arguments.createMap();
                paymentIntentCancelMap.putMap(INTENT,serializePaymentIntent(paymentIntent,lastCurrency));
                sendEventWithName(EVENT_PAYMENT_INTENT_CANCEL,paymentIntentCancelMap);
            }

            @Override
            public void onFailure(@Nonnull TerminalException e) {
                WritableMap errorMap = Arguments.createMap();
                errorMap.putString(ERROR,e.getErrorMessage());
                errorMap.putInt(CODE,e.getErrorCode().ordinal());
                errorMap.putMap(INTENT,serializePaymentIntent(lastPaymentIntent,lastCurrency));
                sendEventWithName(EVENT_PAYMENT_INTENT_CANCEL,errorMap);
            }
        });
    }

    @ReactMethod
    public void processPayment(){
        Terminal.getInstance().processPayment(lastPaymentIntent, new PaymentIntentCallback() {
            @Override
            public void onSuccess(@Nonnull PaymentIntent paymentIntent) {
                lastPaymentIntent = paymentIntent;
                WritableMap processPaymentMap = Arguments.createMap();
                processPaymentMap.putMap(INTENT,serializePaymentIntent(paymentIntent,lastCurrency));
                sendEventWithName(EVENT_PROCESS_PAYMENT,processPaymentMap);
            }

            @Override
            public void onFailure(@Nonnull TerminalException e) {
                WritableMap errorMap = Arguments.createMap();
                errorMap.putString(ERROR,e.getErrorMessage());
                errorMap.putInt(CODE,e.getErrorCode().ordinal());
                errorMap.putString(DECLINE_CODE,e.getApiError().getDeclineCode());
                errorMap.putMap(INTENT,serializePaymentIntent(lastPaymentIntent,lastCurrency));
                sendEventWithName(EVENT_PROCESS_PAYMENT,errorMap);
            }
        });
    }

    @ReactMethod
    public void collectPaymentMethod(){
        pendingCreatePaymentIntent = Terminal.getInstance().collectPaymentMethod(lastPaymentIntent, new PaymentIntentCallback() {
            @Override
            public void onSuccess(@Nonnull PaymentIntent paymentIntent) {
                pendingCreatePaymentIntent = null;
                lastPaymentIntent = paymentIntent;
                WritableMap collectPaymentMethodMap = Arguments.createMap();
                collectPaymentMethodMap.putMap(INTENT,serializePaymentIntent(paymentIntent,lastCurrency));
                sendEventWithName(EVENT_PAYMENT_METHOD_COLLECTION,collectPaymentMethodMap);
            }

            @Override
            public void onFailure(@Nonnull TerminalException e) {
                pendingCreatePaymentIntent = null;
                WritableMap errorMap = Arguments.createMap();
                errorMap.putString(ERROR,e.getErrorMessage());
                errorMap.putInt(CODE,e.getErrorCode().ordinal());
                errorMap.putMap(INTENT,serializePaymentIntent(lastPaymentIntent,lastCurrency));
                sendEventWithName(EVENT_PAYMENT_METHOD_COLLECTION,errorMap);
            }
        });
    }

    @ReactMethod
    public void connectReader(String serialNumber, String locationId){
        Reader selectedReader = null;
        if(discoveredReadersList!=null && discoveredReadersList.size()>0){
            for(Reader reader:discoveredReadersList){
                if(reader!=null){
                    if(reader.getSerialNumber().equals(serialNumber)){
                        selectedReader  = reader;
                    }
                }
            }
        }

        if(selectedReader!=null) {
            InternetConnectionConfiguration config = new InternetConnectionConfiguration();
            Terminal.getInstance().connectInternetReader(selectedReader, config, new ReaderCallback() {
                @Override
                public void onSuccess(@Nonnull Reader reader) {
                    sendEventWithName(EVENT_READER_CONNECTION, serializeReader(reader));
                }

                @Override
                public void onFailure(@Nonnull TerminalException e) {
                    WritableMap errorMap = Arguments.createMap();
                    errorMap.putString(ERROR, e.getErrorMessage());
                    sendEventWithName(EVENT_READER_CONNECTION, errorMap);
                }
            });
        }else{
            WritableMap errorMap = Arguments.createMap();
            errorMap.putString(ERROR, "No reader found with provided serial number");
            sendEventWithName(EVENT_READER_CONNECTION,errorMap);
        }
    }

    @ReactMethod
    public void disconnectReader(){
       if(Terminal.getInstance().getConnectedReader()==null){
           sendEventWithName(EVENT_READER_DISCONNECTION_COMPLETION,Arguments.createMap());
       }else{
           Terminal.getInstance().disconnectReader(new Callback() {
               @Override
               public void onSuccess() {
                   sendEventWithName(EVENT_READER_DISCONNECTION_COMPLETION,Arguments.createMap());
               }

               @Override
               public void onFailure(@Nonnull TerminalException e) {
                    WritableMap errorMap = Arguments.createMap();
                    errorMap.putString(ERROR,e.getErrorMessage());
                    sendEventWithName(EVENT_READER_DISCONNECTION_COMPLETION,errorMap);
               }
           });
       }
    }

    @ReactMethod
    public void getLastReaderEvent(){
        sendEventWithName(EVENT_LAST_READER_EVENT,new Integer(lastReaderEvent.ordinal()));
    }

    @ReactMethod
    public void getConnectedReader(){
        Reader reader = Terminal.getInstance().getConnectedReader();
        sendEventWithName(EVENT_CONNECTED_READER,serializeReader(reader));
    }

    @ReactMethod
    public void abortDiscoverReaders(){
        if(pendingDiscoverReaders!=null && !pendingDiscoverReaders.isCompleted()){
            pendingDiscoverReaders.cancel(new Callback() {
                @Override
                public void onSuccess() {
                    pendingDiscoverReaders = null;
                    sendEventWithName(EVENT_ABORT_DISCOVER_READER_COMPLETION,Arguments.createMap());
                }

                @Override
                public void onFailure(@Nonnull TerminalException e) {
                    WritableMap errorMap  = Arguments.createMap();
                    errorMap.putString(ERROR,e.getErrorMessage());
                    sendEventWithName(EVENT_ABORT_DISCOVER_READER_COMPLETION,errorMap);
                }
            });
        }else{
            sendEventWithName(EVENT_ABORT_DISCOVER_READER_COMPLETION,Arguments.createMap());
        }
    }

    @ReactMethod
    public void abortCreatePayment(){
        if(pendingCreatePaymentIntent!=null && !pendingCreatePaymentIntent.isCompleted()){
            pendingCreatePaymentIntent.cancel(new Callback() {
                @Override
                public void onSuccess() {
                    pendingCreatePaymentIntent = null;
                    sendEventWithName(EVENT_ABORT_CREATE_PAYMENT_COMPLETION,Arguments.createMap());
                }

                @Override
                public void onFailure(@Nonnull TerminalException e) {
                    WritableMap errorMap = Arguments.createMap();
                    errorMap.putString(ERROR,e.getErrorMessage());
                    sendEventWithName(EVENT_ABORT_CREATE_PAYMENT_COMPLETION,errorMap);
                }
            });
        }else{
            sendEventWithName(EVENT_ABORT_CREATE_PAYMENT_COMPLETION,Arguments.createMap());
        }
    }

    @ReactMethod
    public void clearCachedCredentials(){
        Terminal.getInstance().clearCachedCredentials();
    }

    @ReactMethod
    public void abortInstallUpdate(){
        if(pendingInstallUpdate!=null && !pendingInstallUpdate.isCompleted()){
            pendingInstallUpdate.cancel(new Callback() {
                @Override
                public void onSuccess() {
                    pendingInstallUpdate = null;
                    sendEventWithName(EVENT_ABORT_INSTALL_COMPLETION,Arguments.createMap());
                }

                @Override
                public void onFailure(@Nonnull TerminalException e) {
                    WritableMap errorMap = Arguments.createMap();
                    errorMap.putString(ERROR,e.getErrorMessage());
                    sendEventWithName(EVENT_ABORT_INSTALL_COMPLETION,errorMap);
                }
            });
        }else{
            sendEventWithName(EVENT_ABORT_INSTALL_COMPLETION,Arguments.createMap());
        }
    }

    @ReactMethod
    public void getConnectionStatus(){
        ConnectionStatus status = Terminal.getInstance().getConnectionStatus();
        sendEventWithName(EVENT_CONNECTION_STATUS, status.ordinal());
    }

    @ReactMethod
    public void setReaderDisplay(int cartValue, ReadableArray cartItems){
        List<CartLineItem> cartItemsTransformed = new ArrayList();
        if(cartItems!=null){
            for (int i = 0; i <  cartItems.size(); i++) {
                ReadableMap item = cartItems.getMap(i);
                if (item == null) {
                    continue;
                };
                String description = item.getString("description");
                int quantity = item.getInt("quantity");
                int value = item.getInt("value");
                cartItemsTransformed.add(new CartLineItem.Builder(description,quantity,value).build());
            };
        }
       
       
        Cart.Builder cart = new Cart.Builder("gbp", 0, cartValue, cartItemsTransformed);
        Terminal.getInstance().setReaderDisplay(cart.build(), new Callback() {
            @Override
                public void onSuccess() {
                // Placeholder for handling successful operation
                }

            @Override
            public void onFailure(TerminalException e) {
            // Placeholder for handling exception
            }
        });
    }
    @ReactMethod
    public void resetReaderDisplay(){
        Terminal.getInstance().clearReaderDisplay(new Callback() {
            @Override
                public void onSuccess() {
                // Placeholder for handling successful operation
                }

            @Override
            public void onFailure(TerminalException e) {
            // Placeholder for handling exception
            }
        });
    }
    @ReactMethod
    public void getPaymentStatus(){
        PaymentStatus status = Terminal.getInstance().getPaymentStatus();
        sendEventWithName(EVENT_PAYMENT_STATUS, status.ordinal());
    }

    @Override
    public void onUpdateDiscoveredReaders(@Nonnull List<? extends Reader> list) {
        discoveredReadersList = list;
        WritableArray readersDiscoveredArr = Arguments.createArray();
        for(Reader reader : list){
            if(reader!=null){
                readersDiscoveredArr.pushMap(serializeReader(reader));
            }
        }

        sendEventWithName(EVENT_READERS_DISCOVERED,readersDiscoveredArr);
    }

    @Override
    public void fetchConnectionToken(@Nonnull ConnectionTokenCallback connectionTokenCallback) {
        pendingConnectionTokenCallback = connectionTokenCallback;
        sendEventWithName(EVENT_REQUEST_CONNECTION_TOKEN,Arguments.createMap());
    }

    @Override
    public void onReportLowBatteryWarning() {
       sendEventWithName(EVENT_DID_REPORT_LOW_BATTERY_WARNING,Arguments.createMap());
    }

    @Override
    public void onConnectionStatusChange(@Nonnull ConnectionStatus status) {
        WritableMap statusMap = Arguments.createMap();
        statusMap.putInt(STATUS,status.ordinal());
        sendEventWithName(EVENT_DID_CHANGE_CONNECTION_STATUS,statusMap);
    }

    @Override
    public void onReportReaderEvent(@Nonnull ReaderEvent event) {
        lastReaderEvent = event;
        WritableMap readerEventReportMap = Arguments.createMap();
        readerEventReportMap.putInt(EVENT,event.ordinal());
        readerEventReportMap.putMap(INFO,Arguments.createMap());
        sendEventWithName(EVENT_DID_REPORT_READER_EVENT, readerEventReportMap);
    }

    @Override
    public void onPaymentStatusChange(@Nonnull PaymentStatus status) {
        WritableMap paymentStatusMap = Arguments.createMap();
        paymentStatusMap.putInt(STATUS,status.ordinal());
        sendEventWithName(EVENT_DID_CHANGE_PAYMENT_STATUS,paymentStatusMap);
    }

    @Override
    public void onUnexpectedReaderDisconnect(@Nonnull Reader reader) {
        sendEventWithName(EVENT_DID_REPORT_UNEXPECTED_READER_DISCONNECT,serializeReader(reader));
    }

    @Override
    public void onRequestReaderInput(@Nonnull ReaderInputOptions readerInputOptions) {
        WritableMap readerOptionsMap = Arguments.createMap();
        readerOptionsMap.putString(TEXT,readerInputOptions.toString());
        sendEventWithName(EVENT_DID_REQUEST_READER_INPUT,readerOptionsMap);
    }

    @Override
    public void onRequestReaderDisplayMessage(@Nonnull ReaderDisplayMessage readerDisplayMessage) {
        WritableMap displayMessageMap = Arguments.createMap();
        displayMessageMap.putString(TEXT,readerDisplayMessage.toString());
        sendEventWithName(EVENT_DID_REQUEST_READER_DISPLAY_MESSAGE,displayMessageMap);
    }

    @Override
    public void onReportReaderSoftwareUpdateProgress(float v) {
        sendEventWithName(EVENT_DID_REPORT_UPDATE_PROGRESS,new Float(v));
    }

    @Override
    public void onReportAvailableUpdate(ReaderSoftwareUpdate update) {
        sendEventWithName(EVENT_DID_REPORT_AVAILABLE_UPDATE, serializeUpdate(update));
    }

    @Override
    public void onFinishInstallingUpdate(ReaderSoftwareUpdate update, TerminalException e) {
        sendEventWithName(EVENT_DID_FINISH_INSTALLING_UPDATE, serializeUpdate(update));
    }

    @Override
    public void onStartInstallingUpdate(ReaderSoftwareUpdate update, Cancelable cancel) {
        sendEventWithName(EVENT_DID_START_INSTALLING_UPDATE, serializeUpdate(update));
    }
    @ReactMethod
    public void addListener(String eventName) {
        // Keep: Required for RN built in Event Emitter Calls.
    }

    @ReactMethod
    public void removeListeners(Integer count) {
        // Keep: Required for RN built in Event Emitter Calls.
    }
}
