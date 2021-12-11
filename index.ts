import {
  NativeModules,
  NativeEventEmitter,
  EmitterSubscription,
} from "react-native";
import createHooks from "./hooks";

const { RNStripeTerminal } = NativeModules;

export type ListenerCallback<T = null> = (data: T) => void;
type Reader = {
  serialNumber: string;
  deviceType: number;
  batteryLevel: number;
  deviceSoftwareVersion: string;
};
export type ProcessPaymentResolve = {
  amount: number;
  created: Date;
  currency: string;
  metadata: { [key: string]: unknown };
  status: number;
  stripeId: string;
};

export type CartItem = {
  description: string;
  quantity?: number;
  value?: number;
};
class StripeTerminal {
  // Discovery method
  DiscoveryMethodInternet = RNStripeTerminal.DiscoveryMethodInternet;
  // Payment intent statuses
  PaymentIntentStatusRequiresPaymentMethod =
    RNStripeTerminal.PaymentIntentStatusRequiresPaymentMethod;
  PaymentIntentStatusRequiresConfirmation =
    RNStripeTerminal.PaymentIntentStatusRequiresConfirmation;
  PaymentIntentStatusRequiresCapture =
    RNStripeTerminal.PaymentIntentStatusRequiresCapture;
  PaymentIntentStatusCanceled = RNStripeTerminal.PaymentIntentStatusCanceled;
  PaymentIntentStatusSucceeded = RNStripeTerminal.PaymentIntentStatusSucceeded;

  // Reader events
  ReaderEventCardInserted = RNStripeTerminal.ReaderEventCardInserted;
  ReaderEventCardRemoved = RNStripeTerminal.ReaderEventCardRemoved;

  // Payment status
  PaymentStatusNotReady = RNStripeTerminal.PaymentStatusNotReady;
  PaymentStatusReady = RNStripeTerminal.PaymentStatusReady;
  PaymentStatusWaitingForInput = RNStripeTerminal.PaymentStatusWaitingForInput;
  PaymentStatusProcessing = RNStripeTerminal.PaymentStatusProcessing;

  // Connection status
  ConnectionStatusNotConnected = RNStripeTerminal.ConnectionStatusNotConnected;
  ConnectionStatusConnected = RNStripeTerminal.ConnectionStatusConnected;
  ConnectionStatusConnecting = RNStripeTerminal.ConnectionStatusConnecting;

  //Event listener
  listener = new NativeEventEmitter(RNStripeTerminal);

  // Fetch connection token. Overwritten in call to initialize
  _fetchConnectionToken = () =>
    Promise.reject("You must initialize RNStripeTerminal first.");

  constructor() {
    this.listener.addListener("requestConnectionToken", () => {
      this._fetchConnectionToken()
        .then((token) => {
          if (token) {
            RNStripeTerminal.setConnectionToken(token, null);
          } else {
            throw new Error(
              "User-supplied `fetchConnectionToken` resolved successfully, but no token was returned."
            );
          }
        })
        .catch((err) =>
          RNStripeTerminal.setConnectionToken(
            null,
            err.message || "Error in user-supplied `fetchConnectionToken`."
          )
        );
    });
  }

  _wrapPromiseReturn(
    event: string,
    call: () => unknown,
    key?: string
  ): Promise<any> {
    return new Promise((resolve, reject) => {
      const subscription = this.listener.addListener(event, (data) => {
        if (data && data.error) {
          reject(data);
        } else {
          resolve(key ? data[key] : data);
        }
        subscription.remove();
      });

      call();
    });
  }

  initialize({ fetchConnectionToken }): Promise<boolean | string> {
    this._fetchConnectionToken = fetchConnectionToken;
    return new Promise((resolve, reject) => {
      RNStripeTerminal.initialize(
        (status: { isInitialized: boolean; error?: string }) => {
          if (status.isInitialized === true) {
            resolve(true);
          } else {
            reject(status.error);
          }
        }
      );
    });
  }

  discoverReaders(simulated: boolean, locationId: string): Promise<Reader[]> {
    return this._wrapPromiseReturn("readersDiscovered", () => {
      RNStripeTerminal.discoverReaders(
        this.DiscoveryMethodInternet,
        !!simulated ? 1 : 0,
        locationId
      );
    });
  }

  connectReader(serialNumber: string, locationId: string): Promise<Reader> {
    return this._wrapPromiseReturn("readerConnection", () => {
      RNStripeTerminal.connectReader(serialNumber, locationId);
    });
  }

  disconnectReader() {
    return this._wrapPromiseReturn("readerDisconnectCompletion", () => {
      RNStripeTerminal.disconnectReader();
    });
  }

  getConnectedReader(): Promise<Reader> {
    return this._wrapPromiseReturn("connectedReader", () => {
      RNStripeTerminal.getConnectedReader();
    }).then((data) => (data.serialNumber ? data : null));
  }

  getConnectionStatus() {
    return this._wrapPromiseReturn("connectionStatus", () => {
      RNStripeTerminal.getConnectionStatus();
    });
  }
  setTerminalDisplay(value: number, cartItems: CartItem[] = []): Promise<void> {
    return this._wrapPromiseReturn("setTerminalDisplay", () => {
      RNStripeTerminal.setReaderDisplay(value, cartItems);
    });
  }
  clearTerminalDisplay(): Promise<void> {
    return this._wrapPromiseReturn("clearTerminalDisplay", () => {
      RNStripeTerminal.resetReaderDisplay();
    });
  }
  getPaymentStatus(): Promise<unknown> {
    return this._wrapPromiseReturn("paymentStatus", () => {
      RNStripeTerminal.getPaymentStatus();
    });
  }

  getLastReaderEvent(): Promise<number> {
    return this._wrapPromiseReturn("lastReaderEvent", () => {
      RNStripeTerminal.getLastReaderEvent();
    });
  }

  createPayment(paymentIntent: string): Promise<ProcessPaymentResolve> {
    return this._wrapPromiseReturn(
      "paymentCreation",
      () => {
        RNStripeTerminal.createPayment(paymentIntent);
      },
      "intent"
    );
  }

  retrievePaymentIntent(clientSecret) {
    /**
     * Retrieves a pending intent from stripe and stores it in the native SDK.
     * The raw intent should ideally remain in the native SDK and is not returned to JS
     * This intent can have payment collected using the collectPaymentMethod or processPayment if a method is attached.
     */
    return this._wrapPromiseReturn(
      "paymentIntentRetrieval",
      () => {
        RNStripeTerminal.retrievePaymentIntent(clientSecret);
      },
      "intent"
    );
  }

  collectPaymentMethod() {
    /**
     * Should be used in conjunction with retrievePaymentIntent as this will create a pending intent to collect.
     * This will collect the payment from the terminal and return the intent with a payment method attached.
     */
    return this._wrapPromiseReturn(
      "paymentMethodCollection",
      () => {
        RNStripeTerminal.collectPaymentMethod();
      },
      "intent"
    );
  }

  processPayment(): Promise<ProcessPaymentResolve> {
    return this._wrapPromiseReturn(
      "paymentProcess",
      () => {
        RNStripeTerminal.processPayment();
      },
      "intent"
    );
  }

  cancelPaymentIntent() {
    return this._wrapPromiseReturn(
      "paymentIntentCancel",
      () => {
        RNStripeTerminal.cancelPaymentIntent();
      },
      "intent"
    );
  }

  abortCreatePayment() {
    return this._wrapPromiseReturn("abortCreatePaymentCompletion", () => {
      RNStripeTerminal.abortCreatePayment();
    });
  }

  abortDiscoverReaders() {
    return this._wrapPromiseReturn("abortDiscoverReadersCompletion", () => {
      RNStripeTerminal.abortDiscoverReaders();
    });
  }

  abortInstallUpdate() {
    return this._wrapPromiseReturn("abortInstallUpdateCompletion", () => {
      RNStripeTerminal.abortInstallUpdate();
    });
  }

  _addListenerBase(
    eventType: string,
    callback: ListenerCallback
  ): EmitterSubscription["remove"] {
    const subscription = this.listener.addListener(eventType, callback);
    return subscription.remove;
  }

  addLogListener(callback: ListenerCallback) {
    return this._addListenerBase("log", callback);
  }

  addReadersDiscoveredListener(callback: ListenerCallback<Reader[]>) {
    return this._addListenerBase("readersDiscovered", callback);
  }

  addAbortDiscoverReadersCompletionListener(callback: ListenerCallback) {
    return this._addListenerBase("abortDiscoverReadersCompletion", callback);
  }

  addReaderSoftwareUpdateProgressListener(callback: ListenerCallback) {
    return this._addListenerBase("readerSoftwareUpdateProgress", callback);
  }

  addDidRequestReaderInputListener(callback: ListenerCallback) {
    return this._addListenerBase("didRequestReaderInput", callback);
  }

  addDidRequestReaderDisplayMessageListener(callback: ListenerCallback) {
    return this._addListenerBase("didRequestReaderDisplayMessage", callback);
  }

  addDidReportReaderEventListener(callback: ListenerCallback) {
    return this._addListenerBase("didReportReaderEvent", callback);
  }
  addDidReportLowBatteryWarningListener(callback: ListenerCallback) {
    return this._addListenerBase("didReportLowBatteryWarning", callback);
  }

  addDidChangePaymentStatusListener(callback: ListenerCallback) {
    return this._addListenerBase("didChangePaymentStatus", callback);
  }

  addDidChangeConnectionStatusListener(callback: ListenerCallback) {
    return this._addListenerBase("didChangeConnectionStatus", callback);
  }

  addDidReportUnexpectedReaderDisconnectListener(callback: ListenerCallback) {
    return this._addListenerBase(
      "didReportUnexpectedReaderDisconnect",
      callback
    );
  }

  addDidReportAvailableUpdateListener(callback: ListenerCallback) {
    return this._addListenerBase("didReportAvailableUpdate", callback);
  }

  addDidStartInstallingUpdateListener(callback: ListenerCallback) {
    return this._addListenerBase("didStartInstallingUpdate", callback);
  }

  addDidReportReaderSoftwareUpdateProgressListener(callback: ListenerCallback) {
    return this._addListenerBase(
      "didReportReaderSoftwareUpdateProgress",
      callback
    );
  }

  addDidFinishInstallingUpdateListener(callback: ListenerCallback) {
    return this._addListenerBase("didFinishInstallingUpdate", callback);
  }

  addDidBeginWaitingForReaderInputListener(callback: ListenerCallback) {
    return this._addListenerBase("didBeginWaitingForReaderInput", callback);
  }

  addDidRequestReaderInputPromptListener(callback: ListenerCallback) {
    return this._addListenerBase(
      "didRequestReaderInputPromptListener",
      callback
    );
  }
}

const StripeTerminal_ = new StripeTerminal();
export type StripeTerminalType = StripeTerminal;
export default StripeTerminal_;

export const {
  useStripeTerminalState,
  useStripeTerminalCreatePayment,
  useStripeTerminalConnectionManager,
} = createHooks(StripeTerminal_);
