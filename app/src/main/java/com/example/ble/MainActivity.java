package com.example.ble;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.accent_systems.ibks_sdk.scanner.ASBleScanner;
import com.accent_systems.ibks_sdk.scanner.ASScannerCallback;
import com.accent_systems.ibks_sdk.utils.ASUtils;
import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver;
import com.lemmingapex.trilateration.TrilaterationFunction;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;


public class MainActivity extends AppCompatActivity implements ASScannerCallback {

    private void enableBLT() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED)  {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 2);
            }
        }
    }

    private final String TAG = "resulty";
    private final List<String> macAddresses = new ArrayList<>();

    protected RecyclerView mRecyclerView;
    protected RecyclerView.LayoutManager mLayoutManager;
    protected static Adapter mAdapter = new Adapter();


    private Map<String, double[]> coordinates = new HashMap<>();
    private Map<String, Beacon> inBeacon = new HashMap<>();

    private final float mPower = -58.0f;

    private void checkPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,}, 1);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!(requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
            checkPermission();
        }
    }

    public static <K, V extends Comparable<? super V>> Map<String, Beacon> sortByValue(Map<String, Beacon> map) {
        Map<String, Beacon> result = new LinkedHashMap<>();
        Stream<Map.Entry<String, Beacon>> stream = map.entrySet().stream();
        stream.sorted(Comparator.comparing(e -> e.getValue().getRssi())).forEach(e -> result.put(e.getKey(), e.getValue()));

        return result;
    }


    private double calculateDistance(float txPower, double rssi) {
        return Math.pow(10d, (txPower - rssi) / (2 * 10));
    }

    private double[] applyTrilateration(double[][] positions, double[] distances) {
        NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(
                new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
        LeastSquaresOptimizer.Optimum optimum = solver.solve();

        return optimum.getPoint().toArray();
    }

    @Override
    public void scannedBleDevices(ScanResult result) {
        if (macAddresses.contains(result.getDevice().getAddress())) {
            Log.i(TAG, result.getDevice().getAddress());
            if (!inBeacon.containsKey(result.getDevice().getAddress())) {
                inBeacon.put(result.getDevice().getAddress(), new Beacon(result.getDevice().getAddress(),
                        coordinates.get(result.getDevice().getAddress()), result.getRssi()));
            }else {
                inBeacon.get(result.getDevice().getAddress()).setRssi(result.getRssi());
            }
            inBeacon = sortByValue(inBeacon);
            if (inBeacon.size() >= 3) {
                Log.i("inBeacon", String.valueOf(inBeacon.get(inBeacon.keySet().toArray()[inBeacon.size() - 1]).getRssi()));
                Log.i("inBeacon", String.valueOf(inBeacon.get(inBeacon.keySet().toArray()[inBeacon.size() - 2]).getRssi()));
                Log.i("inBeacon", String.valueOf(inBeacon.get(inBeacon.keySet().toArray()[inBeacon.size() - 3]).getRssi()));
                outCoordinates();
            }
        }
    }


    private void outCoordinates() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Wait a minute...");

        if (inBeacon.get(inBeacon.keySet().toArray()[inBeacon.size() - 1]).getRssi() >= 0
                || inBeacon.get(inBeacon.keySet().toArray()[inBeacon.size() - 2]).getRssi() >= 0
                || inBeacon.get(inBeacon.keySet().toArray()[inBeacon.size() - 3]).getRssi() >= 0) {

        }else {
            double[][] positions = {inBeacon.get(inBeacon.keySet().toArray()[inBeacon.size() - 1]).getCoordinates(),
                    inBeacon.get(inBeacon.keySet().toArray()[inBeacon.size() - 2]).getCoordinates(),
                    inBeacon.get(inBeacon.keySet().toArray()[inBeacon.size() - 3]).getCoordinates()};
            double[] distances = {calculateDistance(mPower, inBeacon.get(inBeacon.keySet().toArray()[inBeacon.size() - 1]).getRssi()),
                    calculateDistance(mPower, inBeacon.get(inBeacon.keySet().toArray()[inBeacon.size() - 2]).getRssi()),
                    calculateDistance(mPower, inBeacon.get(inBeacon.keySet().toArray()[inBeacon.size() - 3]).getRssi())};

            double[] coordinates = applyTrilateration(positions, distances);

            mAdapter.addItem(coordinates[0] + " " + coordinates[1]);
            mAdapter.notifyDataSetChanged();
            mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount()-1);

            Log.i(TAG, "item is added");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission();
        enableBLT();

        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        mLayoutManager = new LinearLayoutManager(getApplicationContext());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mAdapter);

        List<String> beaconMacs = new ArrayList<>();
        beaconMacs.add("F1:4B:47:EF:66:34");
        beaconMacs.add("F5:85:D0:B5:D0:B1");
        beaconMacs.add("F4:85:26:15:95:36");

        macAddresses.add(beaconMacs.get(0));
        macAddresses.add(beaconMacs.get(1));
        macAddresses.add(beaconMacs.get(2));

        coordinates.put(beaconMacs.get(0), new double[]{0.0, 0.0});
        coordinates.put(beaconMacs.get(1), new double[]{9.0, 0.0});
        coordinates.put(beaconMacs.get(2), new double[]{10.0, 2.0});


        new ASBleScanner(this, this);

        int e;
        ASBleScanner.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        e = ASBleScanner.startScan();
        if (e != ASUtils.TASK_OK) {
            Log.i(TAG, "startScan - Error (" + e + ")");
        }
    }
}