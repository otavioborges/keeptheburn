package ca.mopicaltechtronic.keeptheburn;

public interface HeartRateView {
    void onHeartRateReceived(int heartRate);
    void onConnectionStateChanged(ConnectionStatus status);
}
