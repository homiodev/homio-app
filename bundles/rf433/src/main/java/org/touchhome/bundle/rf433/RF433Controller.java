package org.touchhome.bundle.rf433;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.bundle.rf433.dto.Rf433JSON;
import org.touchhome.bundle.rf433.model.RF433SignalEntity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

@RestController
@RequestMapping("/rest")
public class RF433Controller {

    private static final String[] BUCKETS = new String[]{"#2f4554", "#c23531", "#e530cd", "#5cd538", "#7ca470", "#2787c7", "#27c7b4", "#b23ddc", "#8500b6", "#f510a1",
            "#bb7c16", "#f99f0f", "#e04e1a", "#f21d56", "#721df2", "#1d80f2", "#95beec", "#11fc7f"};
    @Autowired
    private Rf433Service rf433Service;

    public static void main(String[] args) throws IOException {
        File file = new File("c:\\Users\\Ruslan\\SmartHome\\rf433\\testWave.getPinRequestType");
        BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
        byte[] g = new byte[4];
        bufferedInputStream.read(g);
        ByteBuffer wrap = ByteBuffer.wrap(g);
        System.out.println(wrap.getFloat());
        System.out.println(makeInt(g[3], g[2], g[1], g[0]));

    }

    static private int makeInt(byte b3, byte b2, byte b1, byte b0) {
        return (((b3) << 24) |
                ((b2 & 0xff) << 16) |
                ((b1 & 0xff) << 8) |
                ((b0 & 0xff)));
    }

    @PostMapping("saveRf433Wave")
    public RF433SignalEntity saveRf433Wave(@RequestBody Rf433JSON rf433JSON) throws IOException {
        return rf433Service.save();
    }

  /*  @RequestMapping(value = "/testRf433Transmitter", method = RequestMethod.POST)
    public List<String> testRf433Transmitter(@RequestBody Rf433JSON rf433JSON) throws IOException, InterruptedException {
        Rf433Manager.RF433Signal signal = buildRf433Signal(rf433JSON);

        //PinRepository.PinEntryBinding pinEntryBinding = pinRepository.findByEntityID(RF433DevicePlugin.class, PinPool.INPUT);
       // return rf433Manager.buildAndRunScriptFromSignal(signal, rf433JSON, pinEntryBinding.getSourceBCMPin().getAddress());
        return null;
    }*/

   /* @RequestMapping(value = "/recordRf433", method = RequestMethod.POST)
    public ChartWidgetJSON recordRf433(@RequestBody Rf433JSON rf433JSON) throws IOException, InterruptedException {
        Rf433Manager.RF433Signal signal = buildRf433Signal(rf433JSON);
        WidgetLineChartEntity widgetLineChartEntity = new WidgetLineChartEntity();

        ChartBuilder chartBuilder = ChartBuilder.create(widgetLineChartEntity);
        chartBuilder.withFeature().withDataZoom().withRestore();
        chartBuilder.withDataZoom();

        chartBuilder.withXAxis().data(Arrays.asList(signal.getTimes()));
        chartBuilder.withYAxis().withBoundaryGap(Arrays.asList(0.1, 0.1));
        chartBuilder.withTitle("Signal count: " + signal.getValues().length);

        if (rf433JSON.getShowDuplicates() > 0) {
            List<Rf433Manager.RF433Signal.GroupSignal> signals = signal.findDuplicates2(rf433JSON.getShowDuplicates() / 1000F);

            ChartWidgetJSON.VisualMap visualMap = chartBuilder.visualMap(true, 0);

            for (Rf433Manager.RF433Signal.GroupSignal groupSignal : signals) {
                visualMap.addPiece(groupSignal.from, groupSignal.to, BUCKETS[groupSignal.groupId]);
            }
            visualMap.outOfRange(BUCKETS[0]);
        }
        chartBuilder.withSeries("line", "test").stepEnd().withData(Arrays.asList(signal.getValues()));

        return chartBuilder.build();
    }*/

    /*private Rf433Manager.RF433Signal buildRf433Signal(@RequestBody Rf433JSON rf433JSON) throws IOException, InterruptedException {
        Rf433Manager.RF433Signal signal;
        if (rf433JSON.getForce()) {
            PinRepository.PinEntryBinding pinEntryBinding = pinRepository.findByEntityID(RF433DevicePlugin.class, PinPool.INPUT);
            signal = rf433Manager.readSignal(pinEntryBinding.getSourceBCMPin().getAddress(), rf433JSON);
            SmartUtils.serialize(signal);
        } else {
            signal = SmartUtils.deSerialize(Rf433Manager.RF433Signal.class);
        }

        if (rf433JSON.getOmitDuplicateTime()) {
            signal.omitDuplicateTime();
        }
        if (rf433JSON.getIgnoreNoise() > 0) {
            signal.ignoreNoise(rf433JSON.getIgnoreNoise());
        }
        if (rf433JSON.getTrimTime()) {
            signal.trimTime();
        }
        if (rf433JSON.getJoinSameImpulses()) {
            signal.joinSameImpulses();
        }
        if (!rf433JSON.getZoom().isEmpty()) {
            signal.zoom(rf433JSON.getZoom());
        }
        return signal;
    }*/
}
