import { NativeModules, NativeEventEmitter, Platform } from "react-native";
import createHooks from "./hooks";
import createConnectionService from "./connectionService";

const { RNStripeTerminal } = NativeModules;

class StripeTerminal {
  // Device types
  DeviceTypeChipper2X = RNStripeTerminal.DeviceTypeChipper2X;

  // Discovery methods
  DiscoveryMethodBluetoothScan = RNStripeTerminal.DiscoveryMethodBluetoothScan;
  DiscoveryMethodInternet = RNStripeTerminal.DiscoveryMethodInternet;
  DiscoveryMethodBluetoothProximity =
    RNStripeTerminal.DiscoveryMethodBluetoothProximity;

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

  // Fetch connection token. Overwritten in call to initialize
  _fetchConnectionToken = () =>
    Promise.reject("You must initialize RNStripeTerminal first.");
  listener = new NativeEventEmitter(RNStripeTerminal);

  _wrapPromiseReturn(event: string, call: () => unknown, key?: string) {
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

  initialize({ fetchConnectionToken }) {
    this._fetchConnectionToken = fetchConnectionToken;
    return new Promise((resolve, reject) => {
      if (Platform.OS === "android") {
        RNStripeTerminal.initialize((status) => {
          if (status.isInitialized === true) {
            fetchConnectionToken()
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
                  err.message ||
                    "Error in user-supplied `fetchConnectionToken`."
                )
              );
            resolve();
          } else {
            reject(status.error);
          }
        });
      } else {
        RNStripeTerminal.initialize();
        resolve();
      }
    });
  }

  discoverReaders(method, simulated, locationId) {
    return this._wrapPromiseReturn("readersDiscovered", () => {
      RNStripeTerminal.discoverReaders(method, simulated, locationId);
    });
  }

  checkForUpdate() {
    return this._wrapPromiseReturn(
      "updateCheck",
      () => {
        RNStripeTerminal.checkForUpdate();
      },
      "update"
    );
  }

  installUpdate() {
    return this._wrapPromiseReturn("updateInstall", () => {
      RNStripeTerminal.installUpdate();
    });
  }

  connectReader(serialNumber, locationId) {
    return this._wrapPromiseReturn("readerConnection", () => {
      RNStripeTerminal.connectReader(serialNumber, locationId);
    });
  }

  disconnectReader() {
    return this._wrapPromiseReturn("readerDisconnectCompletion", () => {
      RNStripeTerminal.disconnectReader();
    });
  }

  getConnectedReader() {
    return this._wrapPromiseReturn("connectedReader", () => {
      RNStripeTerminal.getConnectedReader();
    }).then((data) => (data.serialNumber ? data : null));
  }

  getConnectionStatus() {
    return this._wrapPromiseReturn("connectionStatus", () => {
      RNStripeTerminal.getConnectionStatus();
    });
  }
  setTerminalDisplay(value: number) {
    return this._wrapPromiseReturn("connectionStatus", () => {
      RNStripeTerminal.setReaderDisplay(value);
    });
  }

  getPaymentStatus() {
    return this._wrapPromiseReturn("paymentStatus", () => {
      RNStripeTerminal.getPaymentStatus();
    });
  }

  getLastReaderEvent() {
    return this._wrapPromiseReturn("lastReaderEvent", () => {
      RNStripeTerminal.getLastReaderEvent();
    });
  }

  createPayment(options) {
    return this._wrapPromiseReturn(
      "paymentCreation",
      () => {
        RNStripeTerminal.createPayment(options);
      },
      "intent"
    );
  }

  createPaymentIntent(options) {
    return this._wrapPromiseReturn(
      "paymentIntentCreation",
      () => {
        RNStripeTerminal.createPaymentIntent(options);
      },
      "intent"
    );
  }

  retrievePaymentIntent(clientSecret) {
    return this._wrapPromiseReturn(
      "paymentIntentRetrieval",
      () => {
        RNStripeTerminal.retrievePaymentIntent(clientSecret);
      },
      "intent"
    );
  }

  collectPaymentMethod() {
    return this._wrapPromiseReturn(
      "paymentMethodCollection",
      () => {
        RNStripeTerminal.collectPaymentMethod();
      },
      "intent"
    );
  }

  processPayment() {
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

  startService(options) {
    if (typeof options === "string") {
      options = { policy: options };
    }

    if (this._currentService) {
      return Promise.reject(
        "A service is already running. You must stop it using `stopService` before starting a new service."
      );
    }

    this._currentService = createConnectionService(this, options);
    this._currentService.start();
    return this._currentService;
  }

  stopService() {
    if (!this._currentService) {
      return Promise.resolve();
    }

    return this._currentService.stop().then(() => {
      this._currentService = null;
    });
  }
  addLogListener(listener) {
    this.listener.addListener("log", listener);
  }
  removeLogListener(listener) {
    this.listener.removeListener("log", listener);
  }
  addReadersDiscoveredListener(listener) {
    this.listener.addListener("readersDiscovered", listener);
  }
  removeReadersDiscoveredListener(listener) {
    this.listener.removeListener("readersDiscovered", listener);
  }
  addAbortDiscoverReadersCompletionListener(listener) {
    this.listener.addListener("abortDiscoverReadersCompletion", listener);
  }
  removeAbortDiscoverReadersCompletionListener(listener) {
    this.listener.removeListener("abortDiscoverReadersCompletion", listener);
  }
  addReaderSoftwareUpdateProgressListener(listener) {
    this.listener.addListener("readerSoftwareUpdateProgress", listener);
  }
  removeReaderSoftwareUpdateProgressListener(listener) {
    this.listener.removeListener("readerSoftwareUpdateProgress", listener);
  }
  addDidRequestReaderInputListener(listener) {
    this.listener.addListener("didRequestReaderInput", listener);
  }
  removeDidRequestReaderInputListener(listener) {
    this.listener.removeListener("didRequestReaderInput", listener);
  }
  addDidRequestReaderDisplayMessageListener(listener) {
    this.listener.addListener("didRequestReaderDisplayMessage", listener);
  }
  removeDidRequestReaderDisplayMessageListener(listener) {
    this.listener.removeListener("didRequestReaderDisplayMessage", listener);
  }
  addDidReportReaderEventListener(listener) {
    this.listener.addListener("didReportReaderEvent", listener);
  }
  removeDidReportReaderEventListener(listener) {
    this.listener.removeListener("didReportReaderEvent", listener);
  }
  addDidReportLowBatteryWarningListener(listener) {
    this.listener.addListener("didReportLowBatteryWarning", listener);
  }
  removeDidReportLowBatteryWarningListener(listener) {
    this.listener.removeListener("didReportLowBatteryWarning", listener);
  }
  addDidChangePaymentStatusListener(listener) {
    this.listener.addListener("didChangePaymentStatus", listener);
  }
  removeDidChangePaymentStatusListener(listener) {
    this.listener.removeListener("didChangePaymentStatus", listener);
  }
  addDidChangeConnectionStatusListener(listener) {
    this.listener.addListener("didChangeConnectionStatus", listener);
  }
  removeDidChangeConnectionStatusListener(listener) {
    this.listener.removeListener("didChangeConnectionStatus", listener);
  }
  addDidReportUnexpectedReaderDisconnectListener(listener) {
    this.listener.addListener("didReportUnexpectedReaderDisconnect", listener);
  }
  removeDidReportUnexpectedReaderDisconnectListener(listener) {
    this.listener.removeListener(
      "didReportUnexpectedReaderDisconnect",
      listener
    );
  }
  addDidReportAvailableUpdateListener(listener) {
    this.listener.addListener("didReportAvailableUpdate", listener);
  }
  removeDidReportAvailableUpdateListener(listener) {
    this.listener.removeListener("didReportAvailableUpdate", listener);
  }
  addDidStartInstallingUpdateListener(listener) {
    this.listener.addListener("didStartInstallingUpdate", listener);
  }
  removeDidStartInstallingUpdateListener(listener) {
    this.listener.removeListener("didStartInstallingUpdate", listener);
  }
  addDidReportReaderSoftwareUpdateProgressListener(listener) {
    this.listener.addListener(
      "didReportReaderSoftwareUpdateProgress",
      listener
    );
  }
  removeDidReportReaderSoftwareUpdateProgressListener(listener) {
    this.listener.removeListener(
      "didReportReaderSoftwareUpdateProgress",
      listener
    );
  }
  addDidFinishInstallingUpdateListener(listener) {
    this.listener.addListener("didFinishInstallingUpdate", listener);
  }
  removeDidFinishInstallingUpdateListener(listener) {
    this.listener.removeListener("didFinishInstallingUpdate", listener);
  }
  addDidBeginWaitingForReaderInputListener(listener) {
    this.listener.addListener("didBeginWaitingForReaderInput", listener);
  }
  removeDidBeginWaitingForReaderInputListener(listener) {
    this.listener.removeListener("didBeginWaitingForReaderInput", listener);
  }
  addDidRequestReaderInputPromptListener(listener) {
    this.listener.addListener("didRequestReaderInputPromptListener", listener);
  }
  removeDidRequestReaderInputPromptListener(listener) {
    this.listener.removeListener(
      "didRequestReaderInputPromptListener",
      listener
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
