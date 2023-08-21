package org.homio.addon.camera.onvif.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelPipeline;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.entity.VideoPlaybackStorage;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.brand.BrandCameraHasMotionAlarm;
import org.homio.addon.camera.onvif.impl.ReolinkBrandHandler.SearchRequest.Search;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.addon.camera.service.VideoDeviceEndpoint;
import org.homio.addon.camera.ui.UICameraSelectionAttributeValues;
import org.homio.addon.camera.ui.UIVideoActionGetter;
import org.homio.addon.camera.ui.UIVideoActionMetadata;
import org.homio.addon.camera.ui.UIVideoEndpointAction;
import org.homio.api.EntityContext;
import org.homio.api.exception.LoginFailedException;
import org.homio.api.model.OptionModel;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.state.*;
import org.homio.api.ui.action.DynamicOptionLoader;
import org.homio.api.ui.field.selection.UIFieldSelection;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.JsonUtils;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.addon.camera.VideoConstants.*;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Log4j2
@CameraBrandHandler("Reolink")
public class ReolinkBrandHandler extends BaseOnvifCameraBrandHandler implements
        BrandCameraHasMotionAlarm, VideoPlaybackStorage {

    private static final Map<String, EndpointConfiguration> configurations = new HashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();
    private final JsonNode channelParam;
    private long tokenExpiration;
    private String token;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private String requestUrl;

    public ReolinkBrandHandler(OnvifCameraService service) {
        super(service);
        this.channelParam = OBJECT_MAPPER.valueToTree(new ChannelParam());
    }

    @Override
    public String getTitle() {
        return getEntity().getTitle();
    }

    static {
        addConfig(new EndpointConfiguration(ENDPOINT_EXPOSURE, ConfigType.select, "Isp", "exposure"));
        addConfig(new EndpointConfiguration(ENDPOINT_DAY_NIGHT, ConfigType.select, "Isp", "dayNight"));
        addConfig(new EndpointConfiguration(ENDPOINT_BLACK_LIGHT, ConfigType.select, "Isp", "backLight"));
        addConfig(new EndpointConfiguration(ENDPOINT_WHITE_BALANCE, ConfigType.select, "Isp", "whiteBalance"));
        addConfig(new EndpointConfiguration(ENDPOINT_ANTI_FLICKER, ConfigType.select, "Isp", "backLight"));
        addConfig(new EndpointConfiguration(ENDPOINT_IMAGE_ROTATE, ConfigType.bool, "Isp", "rotation"));
        addConfig(new EndpointConfiguration(ENDPOINT_3DNR, ConfigType.bool, "Isp", "nr3d"));
        addConfig(new EndpointConfiguration(ENDPOINT_IMAGE_MIRROR, ConfigType.bool, "Isp", "mirroring"));
        addConfig(new EndpointConfiguration(ENDPOINT_DRC, ConfigType.number, "Isp", "drc"));
        addConfig(new EndpointConfiguration(ENDPOINT_BLUE_GAIN, ConfigType.number, "Isp", "blueGain"));
        addConfig(new EndpointConfiguration(ENDPOINT_BLC, ConfigType.number, "Isp", "blc"));
        addConfig(new EndpointConfiguration(ENDPOINT_RED_GAIN, ConfigType.number, "Isp", "redGain"));

        addConfig(new EndpointConfiguration(ENDPOINT_BGCOLOR, ConfigType.bool, "Osd", "bgcolor"));
        addConfig(new EndpointConfiguration(ENDPOINT_NAME_SHOW, ConfigType.bool, "Osd", "osdChannel/enable"));
        addConfig(new EndpointConfiguration(ENDPOINT_NAME_POSITION, ConfigType.select, "Osd", "osdChannel/pos"));
        addConfig(new EndpointConfiguration(ENDPOINT_DATETIME_SHOW, ConfigType.bool, "Osd", "osdTime/enable"));
        addConfig(new EndpointConfiguration(ENDPOINT_DATETIME_POSITION, ConfigType.select, "Osd", "osdTime/pos"));
        addConfig(new EndpointConfiguration(ENDPOINT_WATERMARK_SHOW, ConfigType.bool, "Osd", "watermark"));

        addConfig(new EndpointConfiguration(ENDPOINT_BRIGHT, ConfigType.number, "Image", "bright"));
        addConfig(new EndpointConfiguration(ENDPOINT_CONTRAST, ConfigType.number, "Image", "contrast"));
        addConfig(new EndpointConfiguration(ENDPOINT_HUE, ConfigType.number, "Image", "hue"));
        addConfig(new EndpointConfiguration(ENDPOINT_SATURATION, ConfigType.number, "Image", "saturation"));
        addConfig(new EndpointConfiguration(ENDPOINT_SHARPEN, ConfigType.number, "Image", "sharpen"));

        addConfig(new EndpointConfiguration(ENDPOINT_RECORD_AUDIO, ConfigType.bool, "Isp", "nr3d"));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_MAIN_RESOLUTION, ConfigType.select, "Enc", "mainStream/size"));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_MAIN_BITRATE, ConfigType.select, "Enc", "mainStream/bitRate"));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_MAIN_FRAMERATE, ConfigType.select, "Enc", "mainStream/frameRate"));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_MAIN_H264_PROFILE, ConfigType.select, "Enc", "mainStream/profile"));

        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_SECONDARY_RESOLUTION, ConfigType.select, "Enc", "mainStream/size"));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_SECONDARY_BITRATE, ConfigType.select, "Enc", "mainStream/bitRate"));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_SECONDARY_FRAMERATE, ConfigType.select, "Enc", "mainStream/frameRate"));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_SECONDARY_H264_PROFILE, ConfigType.select, "Enc", "mainStream/profile"));

        addConfig(new EndpointConfiguration(ENDPOINT_CPU_LOADING, ConfigType.number, "Performance", "cpuUsed")
                .setWritable(false));
        addConfig(new EndpointConfiguration(ENDPOINT_BANDWIDTH, ConfigType.number, "Performance", "codecRate")
                .setWritable(false));
    }

    private static void addConfig(EndpointConfiguration configuration) {
        configurations.put(configuration.endpointId, configuration);
    }

    @Override
    public LinkedHashMap<Long, Boolean> getAvailableDaysPlaybacks(EntityContext entityContext, String profile, Date fromDate,
                                                                  Date toDate) {
        ReolinkBrandHandler reolinkBrandHandler = (ReolinkBrandHandler) getEntity().getService().getBrandHandler();
        SearchRequest request = new SearchRequest(new Search(1, profile, Time.of(fromDate), Time.of(toDate)));
        Root[] root = reolinkBrandHandler.firePost("cmd=Search", true, new ReolinkBrandHandler.ReolinkCmd(1, "Search",
                OBJECT_MAPPER.valueToTree(request)));
        if (root[0].error != null) {
            throw new RuntimeException(
                    "Reolink error fetch days: " + root[0].error.detail + ". RspCode: " + root[0].error.rspCode);
        }
        LinkedHashMap<Long, Boolean> res = new LinkedHashMap<>();
        Calendar cal = Calendar.getInstance();
        ArrayNode statusList = root[0].value.get("SearchResult").withArray("status");
        for (JsonNode status : statusList) {
            cal.set(status.get("year").asInt(), status.get("mon").asInt() - 1, 1, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            for (String item : status.get("table").asText().split("(?!^)")) {
                if (cal.getTimeInMillis() <= toDate.getTime()) {
                    res.put(cal.getTimeInMillis(), Integer.parseInt(item) == 1);
                    cal.set(Calendar.DATE, cal.get(Calendar.DATE) + 1);
                }
            }
        }
        return res;
    }

    @Override
    public URI getPlaybackVideoURL(EntityContext entityContext, String fileId) throws URISyntaxException {
        Path path = CommonUtils.getTmpPath().resolve(fileId);
        if (Files.exists(path)) {
            return path.toUri();
        } else {
            String fullUrl = getAuthUrl("cmd=Download&source=" + fileId + "&output=" + fileId, true);
            return new URI(fullUrl);
        }
    }

    @Override
    public PlaybackFile getLastPlaybackFile(EntityContext entityContext, String profile) {
        Date from = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31));
        List<PlaybackFile> playbackFiles = getPlaybackFiles(entityContext, profile, from, new Date());
        return playbackFiles.isEmpty() ? null : playbackFiles.get(playbackFiles.size() - 1);
    }

    @Override
    public DownloadFile downloadPlaybackFile(EntityContext entityContext, String profile, String fileId, Path path)
            throws Exception {
        String fullUrl = getAuthUrl("cmd=Download&source=" + fileId + "&output=" + fileId, true);
        restTemplate.execute(fullUrl, HttpMethod.GET, null, clientHttpResponse -> {
            StreamUtils.copy(clientHttpResponse.getBody(),
                    Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE));
            return path;
        });

        return new DownloadFile(new UrlResource(path.toUri()), Files.size(path), fileId, null);
    }

    @SneakyThrows
    @Override
    public List<PlaybackFile> getPlaybackFiles(EntityContext entityContext, String profile, Date from, Date to) {
        SearchRequest request = new SearchRequest(new Search(0, profile, Time.of(from), Time.of(to)));
        Root[] root = firePost("cmd=Search", true, new ReolinkBrandHandler.ReolinkCmd(1, "Search",
                OBJECT_MAPPER.valueToTree(request)));
        if (root[0].error != null) {
            throw new RuntimeException("RspCode: " + root[0].error.rspCode + ". Details: " + root[0].error.detail);
        }
        JsonNode files = root[0].value.get("SearchResult").path("File");
        if (files == null) {
            throw new IllegalStateException("Unable to find playback files for date range: " + from + " - " + to);
        }
        List<PlaybackFile> playbackFiles = new ArrayList<>();
        for (JsonNode fileNode : files) {
            File file = OBJECT_MAPPER.treeToValue(fileNode, File.class);
            playbackFiles.add(file.toPlaybackFile());
        }
        return playbackFiles;
    }

    @UIVideoEndpointAction(ENDPOINT_AUTO_LED)
    public void setAutoLed(boolean on) {
        getIRLedHandler().accept(on);
    }

    @Override
    public Consumer<Boolean> getIRLedHandler() {
        return on -> {
            if (firePostGetCode(ReolinkCommand.SetIrLights,
                    new ReolinkCmd(0, "SetIrLights", OBJECT_MAPPER.valueToTree(new SetIrLightsRequest(on))))) {
                setAttribute(ENDPOINT_AUTO_LED, OnOffType.of(on));
                getEntityContext().ui().sendSuccessMessage("Reolink set IR light applied successfully");
            }
        };
    }

    @Override
    public Supplier<Boolean> getIrLedValueHandler() {
        return () -> Optional.ofNullable(getAttribute(ENDPOINT_AUTO_LED)).map(State::boolValue).orElse(false);
    }

    @UIVideoActionGetter(ENDPOINT_AUTO_LED)
    public State isAutoLed() {
        return getAttribute(ENDPOINT_AUTO_LED);
    }

    @UIVideoActionGetter(ENDPOINT_NAME_POSITION)
    public State getNamePosition() {
        return getOSDPosition("osdChannel");
    }

    @UIVideoEndpointAction(ENDPOINT_NAME_POSITION)
    @UICameraSelectionAttributeValues(value = "OsdRange", path = "osdChannel/pos", prependValues = {"", "Hide"})
    public void setNamePosition(String position) {
        setSetting(ENDPOINT_NAME_POSITION, osd -> {
            if ("".equals(position)) {
                osd.set(false, "osdChannel/enable");
            } else {
                osd.set(true, "osdChannel/enable");
                osd.set(position, "osdChannel/pos");
            }
        });
    }

    @UIVideoActionGetter(ENDPOINT_DATETIME_POSITION)
    public State getDateTimePosition() {
        return getOSDPosition("osdTime");
    }

    @UIVideoEndpointAction(ENDPOINT_DATETIME_POSITION)
    @UICameraSelectionAttributeValues(value = "OsdRange", path = "osdTime/pos", prependValues = {"", "Hide"})
    public void setDateTimePosition(String position) {
        setSetting(ENDPOINT_DATETIME_POSITION, osd -> {
            if ("".equals(position)) {
                osd.set(false, "osdTime/enable");
            } else {
                osd.set(true, "osdTime/enable");
                osd.set(position, "osdTime/pos");
            }
        });
    }

    @UIVideoActionGetter(ENDPOINT_WATERMARK_SHOW)
    public State getShowWatermark() {
        JsonType osd = (JsonType) getAttribute("Osd");
        return osd == null ? null : OnOffType.of(osd.getJsonNode().path("watermark").asInt() == 1);
    }

    @UIVideoEndpointAction(ENDPOINT_WATERMARK_SHOW)
    public void setShowWatermark(boolean on) {
        setSetting(ENDPOINT_WATERMARK_SHOW, osd -> osd.set(boolToInt(on), "watermark"));
    }

    @UIVideoActionGetter(ENDPOINT_DATETIME_SHOW)
    public State getShowName() {
        JsonType osd = (JsonType) getAttribute("Osd");
        return osd == null ? null : OnOffType.of(osd.getJsonNode().path("osdChannel").path("enable").asInt() == 1);
    }

    @UIVideoEndpointAction(ENDPOINT_NAME_SHOW)
    public void setShowName(boolean on) {
        setSetting(ENDPOINT_NAME_SHOW, osd -> osd.set(boolToInt(on), "osdChannel/enable"));
    }

    @UIVideoActionGetter(ENDPOINT_DATETIME_SHOW)
    public State getShowDateTime() {
        JsonType osd = (JsonType) getAttribute("Osd");
        return osd == null ? null : OnOffType.of(osd.getJsonNode().path("osdTime").path("enable").asInt() == 1);
    }

    @UIVideoActionGetter(ENDPOINT_IMAGE_ROTATE)
    public State getRotateImage() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : OnOffType.of(isp.getJsonNode().path("rotation").asInt() == 1);
    }

    @UIVideoEndpointAction(ENDPOINT_IMAGE_ROTATE)
    public void setRotateImage(boolean on) {
        setSetting(ENDPOINT_IMAGE_ROTATE, isp -> isp.set(boolToInt(on), "rotation"));
    }

    @UIVideoEndpointAction(ENDPOINT_DATETIME_SHOW)
    public void setShowDateTime(boolean on) {
        setSetting(ENDPOINT_DATETIME_SHOW, osd -> osd.set(boolToInt(on), "osdTime/enable"));
    }

    @UIVideoActionGetter(ENDPOINT_IMAGE_MIRROR)
    public State getMirrorImage() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : OnOffType.of(isp.getJsonNode().path("mirroring").asInt() == 1);
    }

    @UIVideoEndpointAction(ENDPOINT_IMAGE_MIRROR)
    public void setMirrorImage(boolean on) {
        setSetting(ENDPOINT_IMAGE_MIRROR, isp -> isp.set(boolToInt(on), "mirroring"));
    }

    @UIVideoActionGetter(ENDPOINT_ANTI_FLICKER)
    public State getAntiFlicker() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : new StringType(isp.get("antiFlicker").asText());
    }

    @UIVideoEndpointAction(ENDPOINT_ANTI_FLICKER)
    @UICameraSelectionAttributeValues(value = "IspRange", path = "antiFlicker")
    public void setAntiFlicker(String value) {
        setSetting(ENDPOINT_ANTI_FLICKER, isp -> isp.set(value, "antiFlicker"));
    }

    @UIVideoActionGetter(ENDPOINT_EXPOSURE)
    public State getExposure() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : new StringType(isp.get("exposure").asText());
    }

    @UIVideoActionGetter(ENDPOINT_DAY_NIGHT)
    public State getDayNight() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : new StringType(isp.getJsonNode().path("dayNight").asText());
    }

    @UIVideoEndpointAction(ENDPOINT_DAY_NIGHT)
    @UICameraSelectionAttributeValues(value = "IspRange", path = "dayNight")
    public void setDayNight(String value) {
        setSetting(ENDPOINT_DAY_NIGHT, isp -> isp.set(value, "dayNight"));
    }

    @UIVideoEndpointAction(ENDPOINT_EXPOSURE)
    @UICameraSelectionAttributeValues(value = "IspRange", path = "exposure")
    public void setExposure(String value) {
        setSetting(ENDPOINT_EXPOSURE, isp -> isp.set(value, "exposure"));
    }

    @UIVideoActionGetter(ENDPOINT_WHITE_BALANCE)
    public State getWhiteBalance() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : new StringType(isp.getJsonNode().path("whiteBalance").asText());
    }

    @UIVideoEndpointAction(ENDPOINT_WHITE_BALANCE)
    @UICameraSelectionAttributeValues(value = "IspRange", path = "whiteBalance")
    public void setWhiteBalance(String value) {
        setSetting(ENDPOINT_WHITE_BALANCE, isp -> isp.set(value, "whiteBalance"));
    }

    @UIVideoActionGetter(ENDPOINT_RED_GAIN)
    public State getRedGain() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : new DecimalType(isp.getJsonNode().path("redGain").asInt());
    }

    @UIVideoEndpointAction(ENDPOINT_RED_GAIN)
    public void setRedGain(String value) {
        setSetting(ENDPOINT_RED_GAIN, isp -> isp.set(value, "redGain"));
    }

    @UIVideoActionGetter(ENDPOINT_DRC)
    public State getDrc() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : new DecimalType(isp.getJsonNode().path("redGain").asInt());
    }

    @UIVideoEndpointAction(ENDPOINT_DRC)
    public void setDrc(String value) {
        setSetting(ENDPOINT_DRC, isp -> isp.set(value, "drc"));
    }

    @UIVideoActionGetter(ENDPOINT_BLUE_GAIN)
    public State getBlueGain() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : new DecimalType(isp.getJsonNode().path("blueGain").asInt());
    }

    @UIVideoEndpointAction(ENDPOINT_BLUE_GAIN)
    public void setBlueGain(String value) {
        setSetting(ENDPOINT_BLUE_GAIN, isp -> isp.set(value, "blueGain"));
    }

    @UIVideoActionGetter(ENDPOINT_BLC)
    public State getBlc() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : new DecimalType(isp.getJsonNode().path("blc").asInt());
    }

    @UIVideoEndpointAction(ENDPOINT_BLC)
    public void setBlc(String value) {
        setSetting(ENDPOINT_BLC, isp -> isp.set(value, "blc"));
    }

    @UIVideoActionGetter(ENDPOINT_3DNR)
    public State get3DNR() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : OnOffType.of(isp.getJsonNode().path("nr3d").asInt() == 1);
    }

    @UIVideoEndpointAction(ENDPOINT_3DNR)
    public void set3DNR(boolean on) {
        setSetting(ENDPOINT_3DNR, isp -> isp.set(boolToInt(on), "nr3d"));
    }

    @UIVideoActionGetter(ENDPOINT_RECORD_AUDIO)
    public State getRecAudio() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : OnOffType.of(enc.getJsonNode().path("audio").asInt() == 1);
    }

    @UIVideoEndpointAction(ENDPOINT_RECORD_AUDIO)
    @UIVideoActionMetadata(subGroup = "VIDEO.mainStream", subGroupIcon = "fas fa-dice-six")
    public void setRecAudio(boolean on) {
        setSetting(ENDPOINT_RECORD_AUDIO, enc -> enc.set(boolToInt(on), "audio"));
    }

    @UIVideoActionGetter(ENDPOINT_STREAM_MAIN_RESOLUTION)
    public State getStreamMainResolution() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("mainStream/size").asText());
    }

    @UIVideoEndpointAction(ENDPOINT_STREAM_MAIN_RESOLUTION)
    @UIVideoActionMetadata(subGroup = "VIDEO.mainStream", subGroupIcon = "fas fa-dice-six")
    @UIFieldSelection(value = SelectResolution.class, staticParameters = {"mainStream"})
    public void setStreamMainResolution(String value) {
        setSetting(ENDPOINT_STREAM_MAIN_RESOLUTION, enc -> enc.set(value, "mainStream/size"));
    }

    @UIVideoActionGetter(ENDPOINT_STREAM_MAIN_BITRATE)
    public State getStreamMainBitRate() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("mainStream/bitRate").asText());
    }

    @UIVideoEndpointAction(value = ENDPOINT_STREAM_MAIN_BITRATE)
    @UIVideoActionMetadata(subGroup = "VIDEO.mainStream", subGroupIcon = "fas fa-dice-six")
    @UIFieldSelection(value = SelectStreamValue.class, staticParameters = {"mainStream", "bitRate"})
    public void setStreamMainBitRate(String value) {
        setSetting(ENDPOINT_STREAM_MAIN_BITRATE, enc -> enc.set(value, "mainStream/bitRate"));
    }

    @UIVideoActionGetter(ENDPOINT_STREAM_MAIN_FRAMERATE)
    public State getStreamMainFrameRate() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("mainStream/frameRate").asText());
    }

    @UIVideoEndpointAction(ENDPOINT_STREAM_MAIN_FRAMERATE)
    @UIVideoActionMetadata(subGroup = "VIDEO.mainStream", subGroupIcon = "fas fa-dice-six")
    @UIFieldSelection(value = SelectStreamValue.class, staticParameters = {"mainStream", "frameRate"})
    public void setStreamMainFrameRate(String value) {
        setSetting(ENDPOINT_STREAM_MAIN_FRAMERATE, enc -> enc.set(value, "mainStream/frameRate"));
    }

    @UIVideoActionGetter(ENDPOINT_STREAM_MAIN_H264_PROFILE)
    public State getStreamMainH264Profile() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("mainStream/profile").asText());
    }

    @UIVideoEndpointAction(ENDPOINT_STREAM_MAIN_H264_PROFILE)
    @UIVideoActionMetadata(subGroup = "VIDEO.mainStream", subGroupIcon = "fas fa-dice-six")
    @UIFieldSelection(value = SelectStreamValue.class, staticParameters = {"mainStream", "profile"})
    public void setStreamMainH264Profile(String value) {
        setSetting(ENDPOINT_STREAM_MAIN_H264_PROFILE, enc -> enc.set(value, "mainStream/profile"));
    }

    @UIVideoActionGetter(ENDPOINT_STREAM_SECONDARY_RESOLUTION)
    public State getStreamSecondaryResolution() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("subStream/size").asText());
    }

    @UIVideoEndpointAction(ENDPOINT_STREAM_SECONDARY_RESOLUTION)
    @UIVideoActionMetadata(subGroup = "VIDEO.subStream", subGroupIcon = "fas fa-dice-six")
    @UIFieldSelection(value = SelectResolution.class, staticParameters = {"subStream"})
    public void setStreamSecondaryResolution(String value) {
        setSetting(ENDPOINT_STREAM_SECONDARY_RESOLUTION, enc -> enc.set(value, "subStream/size"));
    }

    @UIVideoActionGetter(ENDPOINT_STREAM_SECONDARY_BITRATE)
    public State getStreamSecondaryBitRate() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("subStream/bitRate").asText());
    }

    @UIVideoEndpointAction(ENDPOINT_STREAM_SECONDARY_BITRATE)
    @UIVideoActionMetadata(subGroup = "VIDEO.subStream", subGroupIcon = "fas fa-dice-six")
    @UIFieldSelection(value = SelectStreamValue.class, staticParameters = {"subStream", "bitRate"})
    public void setStreamSecondaryBitRate(String value) {
        setSetting(ENDPOINT_STREAM_SECONDARY_BITRATE, enc -> enc.set(value, "subStream/bitRate"));
    }

    @UIVideoActionGetter(ENDPOINT_STREAM_SECONDARY_FRAMERATE)
    public State getStreamSecondaryFrameRate() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("subStream/frameRate").asText());
    }

    @UIVideoEndpointAction(ENDPOINT_STREAM_SECONDARY_FRAMERATE)
    @UIVideoActionMetadata(subGroup = "VIDEO.subStream", subGroupIcon = "fas fa-dice-six")
    @UIFieldSelection(value = SelectStreamValue.class, staticParameters = {"subStream", "frameRate"})
    public void setStreamSecondaryFrameRate(String value) {
        setSetting(ENDPOINT_STREAM_SECONDARY_FRAMERATE, enc -> enc.set(value, "subStream/frameRate"));
    }

    @UIVideoActionGetter(ENDPOINT_STREAM_SECONDARY_H264_PROFILE)
    public State getStreamSecondaryH264Profile() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("subStream/profile").asText());
    }

    @UIVideoEndpointAction(value = ENDPOINT_STREAM_SECONDARY_H264_PROFILE)
    @UIVideoActionMetadata(subGroup = "VIDEO.subStream", subGroupIcon = "fas fa-dice-six")
    @UIFieldSelection(value = SelectStreamValue.class, staticParameters = {"subStream", "profile"})
    public void setStreamSecondaryH264Profile(String value) {
        setSetting(ENDPOINT_STREAM_SECONDARY_H264_PROFILE, isp -> isp.set(value, "subStream/profile"));
    }

    @Override
    public @Nullable String getSnapshotUri() {
        return "/cgi-bin/api.cgi?cmd=Snap&channel=%s&rs=homio".formatted(nvrChannel);
    }

    @Override
    public void postInitializeCamera(EntityContext entityContext) {
        AtomicReference<HttpRequest> request = new AtomicReference<>(createHttpPostRequest());
        service.addLowRequest(() -> {
            Curl.sendAsync(request.get(), Root[].class, (roots, status) -> {
                if (roots[0].getError() != null && roots[0].getError().rspCode == -6) {
                    // fire update url with new token
                    this.tokenExpiration = 0;
                    request.set(createHttpPostRequest());
                } else {
                    JsonNode performance = roots[0].value.path("Performance");
                    if (performance.has("cpuUsed")) {
                        service.getEndpoints().get(ENDPOINT_CPU_LOADING).setValue(new DecimalType(performance.get("cpuUsed").asInt()), true);
                    }
                    if (performance.has("codecRate")) {
                        service.getEndpoints().get(ENDPOINT_BANDWIDTH).setValue(new DecimalType(performance.get("codecRate").asInt()), true);
                    }
                }
            });
        });
    }

    private HttpRequest createHttpPostRequest() {
        return Curl.createPostRequest(getAuthUrl("cmd=GetPerformance", true), "[{cmd: \"GetPerformance\", action: 0, param: {}}]");
    }

    @Override
    public void fetchDataFromCamera() {
        loginIfRequire();

        Root[] roots = firePost("", true,
                new ReolinkCmd(1, "GetDevInfo", channelParam),
                new ReolinkCmd(1, "GetHddInfo", channelParam),
                new ReolinkCmd(1, "GetPerformance", channelParam),
                new ReolinkCmd(1, "GetLocalLink", channelParam),
                new ReolinkCmd(1, "GetIrLights", channelParam),
                new ReolinkCmd(1, "GetOsd", channelParam),
                new ReolinkCmd(1, "GetEnc", channelParam),
                new ReolinkCmd(1, "GetImage", channelParam),
                new ReolinkCmd(1, "GetIsp", channelParam));
        if (roots != null) {
            for (Root objectNode : roots) {
                String cmd = objectNode.cmd;
                String group = cmd.substring("Get".length());
                JsonNode targetNode = objectNode.value.get(group);
                setAttribute(group, new JsonType(targetNode));
                if (objectNode.range != null && objectNode.range.has(group)) {
                    setAttribute(group + "Range", new JsonType(objectNode.range.path(group)));
                }
                if (objectNode.range == null) {
                    objectNode.range = OBJECT_MAPPER.createObjectNode();
                }
                if (!customHandle(objectNode, cmd)) {
                    JsonNode configNode = objectNode.range.path(group);
                    configurations.values().stream().filter(c -> c.group.equals(group)).forEach(config -> {
                        if (JsonUtils.hasJsonPath(targetNode, config.key)) {
                            config.type.handler.handle(configNode, config, this);
                        }
                    });
                }
            }
        }
    }

    private boolean customHandle(Root objectNode, String cmd) {
        return switch (cmd) {
            case "GetIrLights" -> {
                handleGetIrLightCommand(objectNode);
                yield true;
            }
            case "GetLocalLink" -> {
                handleGetLocalLinkCommand(objectNode);
                yield true;
            }
            case "GetHddInfo" -> {
                handleGetHddInfoCommand(objectNode);
                yield true;
            }
            case "GetDevInfo" -> {
                handleGetDevInfoCommand(objectNode);
                yield true;
            }
            default -> false;
        };
    }

    private void handleGetDevInfoCommand(Root objectNode) {
        JsonNode devInfo = objectNode.value.get("DevInfo");
        String model = devInfo.path("model").asText("");
        String hardVer = devInfo.path("hardVer").asText("");
        if ((isNotEmpty(model) && !model.equals(getEntity().getModel()))
                || (isNotEmpty(hardVer) && !hardVer.equals(getEntity().getHardwareId()))) {
            getEntityContext().updateDelayed(getEntity(), entity -> {
                entity.setModel(model);
                entity.setHardwareId(hardVer);
            });
        }
    }

    private void handleGetHddInfoCommand(Root objectNode) {
        JsonNode hddInfo = objectNode.value.withArray("HddInfo").path(0);
        setAttribute(ENDPOINT_HDD, new JsonType(hddInfo));
        service.addEndpoint(ENDPOINT_HDD, key -> {
            VideoDeviceEndpoint endpoint = new VideoDeviceEndpoint(getEntity(), key, DeviceEndpoint.EndpointType.string, false);
            String text = hddInfo.get("size").asText() + "/" + hddInfo.get("capacity").asText();
            endpoint.setValue(new StringType(text), true);
            return endpoint;
        }, state -> {
        });
    }

    private void handleGetLocalLinkCommand(Root objectNode) {
        JsonNode localLink = objectNode.value.get("LocalLink");
        String link = localLink.path("activeLink").asText("");
        String mac = localLink.path("mac").asText("").toUpperCase();
        if ((isEmpty(getEntity().getMac()) && isNotEmpty(mac))
                || (isEmpty(getEntity().getActiveLink()) && isNotEmpty(link))) {
            getEntityContext().updateDelayed(getEntity(), entity -> {
                entity.setActiveLink(link);
                entity.setMac(mac);
            });
        }
    }

    private void handleGetIrLightCommand(Root objectNode) {
        setAttribute(ENDPOINT_AUTO_LED, OnOffType.of("auto".equals(objectNode.value.get("IrLights").get("state").asText())));
        service.addEndpointSwitch(ENDPOINT_AUTO_LED, state -> setAutoLed(state.boolValue()), true)
                .setValue(isAutoLed(), true);
    }

    @Override
    public String updateURL(String url) {
        loginIfRequire();
        return url.isEmpty() ? "token=" + token : url + "&token=" + token;
    }

    @Override
    public boolean isSupportOnvifEvents() {
        return true;
    }

    @SneakyThrows
    public Root[] firePost(String url, boolean requireAuth, ReolinkCmd... commands) {
        String fullUrl = getAuthUrl(url, requireAuth);
        var requestEntity = RequestEntity.post(new URL(fullUrl).toURI())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Arrays.asList(commands));
        ResponseEntity<String> exchange = restTemplate.exchange(requestEntity, String.class);
        if (exchange.getBody() == null) {
            return null;
        }
        Root[] roots = new ObjectMapper().readValue(exchange.getBody(), Root[].class);
        // re-login if require
        if (roots[0].getError() != null && roots[0].getError().rspCode == -6) {
            this.tokenExpiration = 0;
            return firePost(url, requireAuth, commands);
        }
        return roots;
    }

    public String getAuthUrl(String url, boolean requireAuth) {
        if (requireAuth) {
            url = updateURL(url);
        }
        return "http://" + this.ip + ":" + getEntity().getRestPort() + "/cgi-bin/api.cgi?" + url;
    }

    @Override
    public void setMotionAlarmThreshold(int threshold) {

    }

    @Nullable
    private State getOSDPosition(String path) {
        JsonType osd = (JsonType) getAttribute("Osd");
        if (osd == null) {
            return null;
        }
        return new StringType(osd.getJsonNode().path(path).path("pos").asText("---"));
    }

    private void loginIfRequire() {
        if (this.tokenExpiration - System.currentTimeMillis() < 60000) {
            OnvifCameraEntity entity = getEntity();
            LoginRequest request = new LoginRequest(new LoginRequest.User(entity.getUser(), entity.getPassword().asString()));
            Root root = firePost("cmd=Login", false, new ReolinkCmd(0, "Login", OBJECT_MAPPER.valueToTree(request)))[0];
            if (root.getError() != null) {
                throw new LoginFailedException(root.getError().toString());
            }
            this.tokenExpiration = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(root.value.get("Token").get("leaseTime").asLong());
            this.token = root.value.get("Token").get("name").asText();
        }
    }

    @SneakyThrows
    private boolean firePostGetCode(ReolinkCommand command, ReolinkCmd... commands) {
        Root root = firePost("cmd=" + command.name(), true, commands)[0];
        if (root == null) {
            return false;
        }
        if (root.getError() == null) {
            return true;
        }
        getEntityContext().ui().sendErrorMessage("Error while updating reolink settings. " + root.getError().getDetail());
        return false;
    }

    private void setSetting(String endpointID, Consumer<JsonType> updateHandler) {
        EndpointConfiguration configuration = configurations.get(endpointID);
        JsonType setting = (JsonType) getAttribute(configuration.group);
        if (setting == null) {
            return;
        }
        updateHandler.accept(setting);
        loginIfRequire();
        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.set(configuration.group, setting.getJsonNode());
        ReolinkCommand command = ReolinkCommand.valueOf("Set" + configuration.group);
        if (firePostGetCode(command,
                new ReolinkCmd(0, command.name(), request))) {
            getEntityContext().ui().sendSuccessMessage("Reolink '" + configuration.key + "' changed  successfully");
        }
    }

    private void setSetting(String endpointID, State state) {
        EndpointConfiguration configuration = configurations.get(endpointID);
        JsonType setting = (JsonType) getAttribute(configuration.group);
        if (setting == null) {
            return;
        }
        if (state instanceof OnOffType || state instanceof DecimalType) {
            setting.set(state.intValue(), configuration.key);
        } else {
            setting.set(state.stringValue(), configuration.key);
        }
        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.set(configuration.group, setting.getJsonNode());
        ReolinkCommand command = ReolinkCommand.valueOf("Set" + configuration.group);
        if (firePostGetCode(command,
                new ReolinkCmd(0, command.name(), request))) {
            getEntityContext().ui().sendSuccessMessage("Reolink '" + configuration.key + "' changed  successfully");
        }
    }

    enum ReolinkCommand {
        SetOsd, SetIsp, SetEnc, SetIrLights
    }

    @Getter
    @AllArgsConstructor
    private static class LoginRequest {

        @JsonProperty("User")
        private User user;

        @Getter
        @AllArgsConstructor
        private static class User {

            String userName;
            String password;
        }
    }

    @Getter
    public static class ChannelParam {

        private final int channel = 0;
    }

    @Getter
    @RequiredArgsConstructor
    public static class ReolinkCmd {

        private final int action;
        private final String cmd;
        private final JsonNode param;
    }

    @Getter
    @RequiredArgsConstructor
    public static class SearchRequest {

        @JsonProperty("Search")
        private final Search search;

        @Getter
        @RequiredArgsConstructor
        public static class Search {

            private final int channel = 0;
            private final int onlyStatus;
            private final String streamType;
            @JsonProperty("StartTime")
            private final Time startTime;
            @JsonProperty("EndTime")
            private final Time endTime;
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Time {

        private int year;
        private int mon;
        private int day;
        private int hour;
        private int min;
        private int sec;

        public static Time of(Date date) {
            LocalDateTime time = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            return new Time(time.getYear(), time.getMonthValue(), time.getDayOfMonth(), time.getHour(), time.getMinute(),
                    time.getSecond());
        }

        public static Date from(Time time) {
            return Date.from(
                    LocalDateTime.of(time.year, time.mon, time.day, time.hour, time.min, time.sec).atZone(ZoneId.systemDefault())
                            .toInstant());
        }
    }

    @RequiredArgsConstructor
    private enum ConfigType {
        number((config, c, handler) -> {
            Consumer<State> setter = state -> handler.setSetting(c.endpointId, state);
            Supplier<State> getter = () -> {
                JsonType group = (JsonType) handler.getAttribute(c.group);
                return group == null ? null : new DecimalType(group.getJsonNode().path(c.key).asInt());
            };
            JsonNode numConfigPath = JsonUtils.getJsonPath(config, c.key);
            int min = numConfigPath.path("min").asInt();
            int max = numConfigPath.path("max").asInt();
            handler.service.addEndpointSlider(c.endpointId, min, max, setter, c.writable).setValue(getter.get(), true);
        }),
        bool((config, c, handler) -> {
            Consumer<State> setter = state -> handler.setSetting(c.endpointId, state);
            Supplier<State> getter = () -> {
                JsonType group = (JsonType) handler.getAttribute(c.group);
                return group == null ? null : OnOffType.of(group.getJsonNode().path(c.key).asInt() == 1);
            };
            handler.service.addEndpointSwitch(c.endpointId, setter, c.writable).setValue(getter.get(), true);
        }),
        select((config, c, handler) -> {
            Set<String> range = new LinkedHashSet<>();
            JsonUtils.getJsonPath(config, c.key).forEach(jsonNode -> range.add(jsonNode.asText()));
            Consumer<State> setter = state -> handler.setSetting(c.endpointId, state);
            Supplier<State> getter = () -> {
                JsonType group = (JsonType) handler.getAttribute(c.group);
                return group == null ? null : new StringType(JsonUtils.getJsonPath(group.getJsonNode(), c.key).asText());
            };
            handler.service.addEndpointEnum(c.endpointId, range, setter).setValue(getter.get(), true);
        });
        private final ConfigTypeHandler handler;
    }

    private interface ConfigTypeHandler {

        void handle(JsonNode config, EndpointConfiguration c, ReolinkBrandHandler handler);
    }

    @Getter
    public static class SetIrLightsRequest {

        @SuppressWarnings("WriteOnlyObject")
        @JsonProperty("IrLights")
        private final IrLights irLights = new IrLights();

        public SetIrLightsRequest(Boolean on) {
            irLights.state = on ? "Auto" : "Off";
        }

        @Getter
        public static class IrLights {

            private String state;
        }
    }

    private class SelectResolution implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            List<OptionModel> result = new ArrayList<>();
            JsonType encRange = (JsonType) ReolinkBrandHandler.this.getAttribute("EncRange");
            if (encRange != null && encRange.getJsonNode().isArray()) {
                for (JsonNode jsonNode : encRange.getJsonNode()) {
                    result.add(OptionModel.key(jsonNode.get(parameters.getStaticParameters()[0]).get("size").asText()));
                }
            }
            return result;
        }
    }

    private class SelectStreamValue implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(DynamicOptionLoaderParameters parameters) {
            JsonType encRange = (JsonType) ReolinkBrandHandler.this.getAttribute("EncRange");
            JsonType enc = (JsonType) getAttribute("Enc");
            if (enc != null && encRange != null && encRange.getJsonNode().isArray()) {
                String size = enc.get(parameters.getStaticParameters()[0] + "/size").asText();
                for (JsonNode jsonNode : encRange.getJsonNode()) {
                    JsonNode streamJsonNode = jsonNode.get(parameters.getStaticParameters()[0]);
                    if (streamJsonNode.get("size").asText().equals(size)) {
                        return OptionModel.list(streamJsonNode.get(parameters.getStaticParameters()[1]));
                    }
                }
            }
            return Collections.emptyList();
        }
    }

    @Override
    public void handleSetURL(ChannelPipeline pipeline, String httpRequestURL) {
        requestUrl = httpRequestURL;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class File {

        @JsonProperty("StartTime")
        private Time startTime;
        @JsonProperty("EndTime")
        private Time endTime;
        private int frameRate;
        private int height;
        private String name;
        private int size;
        private String type;
        private int width;

        public PlaybackFile toPlaybackFile() {
            return new PlaybackFile(name, name, Time.from(startTime), Time.from(endTime), size, type);
        }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Root {

        private String cmd;
        private int code;
        private Error error;
        private ObjectNode value;
        private ObjectNode initial;
        private ObjectNode range;

        @Getter
        @Setter
        @ToString
        public static class Error {

            private String detail;
            private int rspCode;
        }
    }

    @Accessors(chain = true)
    @RequiredArgsConstructor
    private static final class EndpointConfiguration {

        private final String endpointId;
        private final ConfigType type;
        private final String group;
        private final String key;
        @Setter
        private boolean writable = true;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            EndpointConfiguration that = (EndpointConfiguration) o;
            return endpointId.equals(that.endpointId);
        }

        @Override
        public int hashCode() {
            return endpointId.hashCode();
        }
    }
}
