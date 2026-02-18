package ca.mopicaltechtronic.keeptheburn;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BleDeviceAdapter extends RecyclerView.Adapter<BleDeviceAdapter.ViewHolder> {

    public interface OnDeviceClickListener {
        void onDeviceClick(BleDevice device);
    }

    private final List<BleDevice> devices;
    private final OnDeviceClickListener listener;

    public BleDeviceAdapter(List<BleDevice> devices, OnDeviceClickListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, mac, rssi;

        public ViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.tvDeviceName);
            mac = view.findViewById(R.id.tvDeviceMac);
            rssi = view.findViewById(R.id.tvRssi);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        BleDevice device = devices.get(position);

        holder.name.setText(device.name != null ? device.name : "Unknown Device");
        holder.mac.setText(device.mac);
        holder.rssi.setText("RSSI: " + device.rssi);

        holder.itemView.setOnClickListener(v -> listener.onDeviceClick(device));
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }
}
