package com.numaolab;

import com.impinj.octane.*;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class App implements TagReportListener {

    MqttClient client;

    App() {
        // MQTT Clientの初期化
        String broker = "tcp://" + System.getenv("MQTT_HOST") + ":" + System.getenv("MQTT_PORT");
        String clientId = System.getenv("MQTT_CLIENT_ID");
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            this.client = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            this.client.connect(connOpts);
            System.out.println("MQTT Connected: " + broker);
        } catch (Exception me) {
            me.printStackTrace();
        }

        // Readerの初期化
        try {
            ImpinjReader reader = new ImpinjReader();
            String address = System.getenv("READER_HOST");
            reader.connect(address);
            Settings settings = reader.queryDefaultSettings();
            settings.setRfMode(0); // https://support.impinj.com/hc/en-us/articles/360000046899-Reader-Modes-RF-Modes-Made-Easy
            // settings.setSearchMode(SearchMode.ReaderSelected);
            // AutoStartConfig stac = settings.getAutoStart();
            // stac.setMode(AutoStartMode.Immediate);
            // AutoStopConfig stoc = settings.getAutoStop();
            // stoc.setMode(AutoStopMode.None);
            ReportConfig rc = settings.getReport();
            rc.setMode(ReportMode.Individual);
            rc.setIncludePeakRssi(true); // RSSIの設定
            rc.setIncludePhaseAngle(true); // Phaseの設定
            rc.setIncludeFirstSeenTime(true); // FirstSeenTimeの設定
            rc.setIncludeLastSeenTime(true); // LastSeenTimeの設定
            TagFilter tf = settings.getFilters().getTagFilter1();
            tf.setBitCount(8);
            tf.setBitPointer(BitPointers.Epc);
            tf.setMemoryBank(MemoryBank.Epc);
            tf.setFilterOp(TagFilterOp.Match);
            tf.setTagMask("02");
            FilterSettings fs = settings.getFilters();
            fs.setMode(TagFilterMode.OnlyFilter1);
            reader.applySettings(settings);
            reader.setTagReportListener(this);
            reader.start();
            System.out.println("READER Started: " + address);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    @Override
    public void onTagReported(ImpinjReader reader, TagReport report) {
        report.getTags().parallelStream().forEach(t -> {
            // epcを2進数に変換
            String bin = "";
            String[] splited = t.getEpc().toString().split(" ");
            for (int i = 0; i < splited.length; i++) {
                int dec = Integer.parseInt(splited[i], 16);
                String b = Integer.toBinaryString(dec);
                if (b.length() < 16) {
                    int need = 16 - b.length();
                    for (int j = 0; j < need; j++) {
                        b = '0' + b;
                    }
                }
                bin = bin + b;
            }
            System.out.println(bin);
            // json文字列を作成
            String template = "{\"header\":\"%s\",\"other\":\"%s\",\"env\":\"%s\",\"eri\":\"%s\",\"logic\":\"%s\",\"k\":\"%s\",\"kd\":\"%s\",\"gid\":\"%s\",\"mbit\":\"%s\",\"igs\":\"%s\",\"rssi\":\"%s\",\"phase\":\"%s\",\"time\":\"%s\"}";
            String jsonString = String.format(template, bin.substring(0, 8), bin.substring(0, 12), bin.substring(12, 20), bin.substring(20, 24), bin.substring(24, 40), bin.substring(40, 44), bin.substring(44, 48), bin.substring(48, 80), bin.substring(80, 81), bin.substring(81, 96), t.getPeakRssiInDbm(), t.getPhaseAngleInRadians(), t.getFirstSeenTime().ToString());
            // MQTTに配信
            try {
                client.publish("all", jsonString.getBytes(), 0, false);
            } catch (MqttPersistenceException e) {} catch (MqttException e) {}
        });
    }

    public static void main( String[] args ) {
        new App();
    }
}
