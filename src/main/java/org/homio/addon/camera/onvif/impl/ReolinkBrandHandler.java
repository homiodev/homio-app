package org.homio.addon.camera.onvif.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelPipeline;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.ToString;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.entity.VideoPlaybackStorage;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.brand.BrandCameraHasMotionAlarm;
import org.homio.addon.camera.onvif.util.IpCameraBindingConstants;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.addon.camera.ui.UICameraSelectionAttributeValues;
import org.homio.addon.camera.ui.UIVideoAction;
import org.homio.addon.camera.ui.UIVideoActionGetter;
import org.homio.api.EntityContext;
import org.homio.api.exception.LoginFailedException;
import org.homio.api.model.OptionModel;
import org.homio.api.state.JsonType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.ui.action.DynamicOptionLoader;
import org.homio.api.ui.field.selection.UIFieldSelection;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

@CameraBrandHandler("Reolink")
public class ReolinkBrandHandler extends BaseOnvifCameraBrandHandler implements
    BrandCameraHasMotionAlarm, VideoPlaybackStorage {

    private final RestTemplate restTemplate = new RestTemplate();
    private long tokenExpiration;
    private String token;
    private String requestUrl;

    public ReolinkBrandHandler(OnvifCameraService service) {
        super(service);
    }

    @Override
    public String getTitle() {
        return getEntity().getTitle();
    }

    @Override
    public LinkedHashMap<Long, Boolean> getAvailableDaysPlaybacks(EntityContext entityContext, String profile, Date fromDate,
        Date toDate) {
        ReolinkBrandHandler reolinkBrandHandler = (ReolinkBrandHandler) getEntity().getService().getBrandHandler();
        Root[] root = reolinkBrandHandler.firePost("cmd=Search", true, new ReolinkBrandHandler.ReolinkCmd(1, "Search",
            new SearchRequest(new SearchRequest.Search(1, profile, Time.of(fromDate), Time.of(toDate)))));
        if (root[0].error != null) {
            throw new RuntimeException(
                "Reolink error fetch days: " + root[0].error.detail + ". RspCode: " + root[0].error.rspCode);
        }
        LinkedHashMap<Long, Boolean> res = new LinkedHashMap<>();
        Calendar cal = Calendar.getInstance();
        for (Status status : root[0].value.searchResult.status) {
            cal.set(status.year, status.mon - 1, 1, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            for (String item : status.table.split("(?!^)")) {
                if (cal.getTimeInMillis() <= toDate.getTime()) {
                    res.put(cal.getTimeInMillis(), Integer.parseInt(item) == 1);
                    cal.set(Calendar.DATE, cal.get(Calendar.DATE) + 1);
                }
            }
        }
        return res;
    }

    @Override
    public List<PlaybackFile> getPlaybackFiles(EntityContext entityContext, String profile, Date from, Date to) {
        Root[] root = firePost("cmd=Search", true, new ReolinkBrandHandler.ReolinkCmd(1, "Search",
            new SearchRequest(new SearchRequest.Search(0, profile, Time.of(from), Time.of(to)))));
        if (root[0].error != null) {
            throw new RuntimeException("RspCode: " + root[0].error.rspCode + ". Details: " + root[0].error.detail);
        }
        List<File> file = root[0].value.searchResult.file;
        if (file == null) {
            throw new IllegalStateException("Unable to find playback files for date range: " + from + " - " + to);
        }
        return file.stream().map(File::toPlaybackFile).collect(Collectors.toList());
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

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_AUTO_LED, order = 10, icon = "fas fa-lightbulb")
    public void autoLed(boolean on) {
        getIRLedHandler().accept(on);
    }

    @Override
    public Consumer<Boolean> getIRLedHandler() {
        return on -> {
            SetIrLightsRequest request = new SetIrLightsRequest();
            request.irLights.state = on ? "Auto" : "Off";
            if (firePostGetCode(ReolinkCommand.SetIrLights, true,
                new ReolinkCmd(0, "SetIrLights", request))) {
                setAttribute(IpCameraBindingConstants.CHANNEL_AUTO_LED, OnOffType.of(on));
                getEntityContext().ui().sendSuccessMessage("Reolink set IR light applied successfully");
            }
        };
    }

    @Override
    public Supplier<Boolean> getIrLedValueHandler() {
        return () -> Optional.ofNullable(getAttribute(IpCameraBindingConstants.CHANNEL_AUTO_LED)).map(State::boolValue).orElse(false);
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_AUTO_LED)
    public State isAutoLed() {
        return getAttribute(IpCameraBindingConstants.CHANNEL_AUTO_LED);
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_POSITION_NAME)
    public State getNamePosition() {
        return getOSDPosition("osdChannel");
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_POSITION_NAME, order = 100, icon = "fas fa-sort-amount-down-alt", group = "VIDEO.OSD")
    @UICameraSelectionAttributeValues(value = "OsdRange", path = {"osdChannel", "pos"}, prependValues = {"", "Hide"})
    public void setNamePosition(String position) {
        setSetting(ReolinkCommand.SetOsd, osd -> {
            if ("".equals(position)) {
                osd.set(false, "osdChannel", "enable");
            } else {
                osd.set(true, "osdChannel", "enable");
                osd.set(position, "osdChannel", "pos");
            }
        });
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_POSITION_DATETIME)
    public State getDateTimePosition() {
        return getOSDPosition("osdTime");
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_POSITION_DATETIME, order = 101, icon = "fas fa-sort-numeric-up", group = "VIDEO.OSD")
    @UICameraSelectionAttributeValues(value = "OsdRange", path = {"osdTime", "pos"}, prependValues = {"", "Hide"})
    public void setDateTimePosition(String position) {
        setSetting(ReolinkCommand.SetOsd, osd -> {
            if ("".equals(position)) {
                osd.set(false, "osdTime", "enable");
            } else {
                osd.set(true, "osdTime", "enable");
                osd.set(position, "osdTime", "pos");
            }
        });
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_SHOW_WATERMARK)
    public State getShowWatermark() {
        JsonType osd = (JsonType) getAttribute("Osd");
        return osd == null ? null : OnOffType.of(osd.getJsonNode().path("watermark").asInt() == 1);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_SHOW_WATERMARK, order = 102, icon = "fas fa-copyright", group = "VIDEO.OSD")
    public void setShowWatermark(boolean on) {
        setSetting(ReolinkCommand.SetOsd, osd -> osd.set(boolToInt(on), "watermark"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_SHOW_DATETIME)
    public State getShowDateTime() {
        JsonType osd = (JsonType) getAttribute("Osd");
        return osd == null ? null : OnOffType.of(osd.getJsonNode().path("osdTime").path("enable").asInt() == 1);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_SHOW_DATETIME, order = 103, icon = "fas fa-copyright", group = "VIDEO.OSD")
    public void setShowDateTime(boolean on) {
        setSetting(ReolinkCommand.SetOsd, osd -> osd.set(boolToInt(on), "osdTime", "enable"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_IMAGE_ROTATE)
    public State getRotateImage() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : OnOffType.of(isp.getJsonNode().path("rotation").asInt() == 1);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_IMAGE_ROTATE, order = 160, icon = "fas fa-copyright", group = "VIDEO.ISP")
    public void setRotateImage(boolean on) {
        setSetting(ReolinkCommand.SetIsp, isp -> isp.set(boolToInt(on), "rotation"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_IMAGE_MIRROR)
    public State getMirrorImage() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : OnOffType.of(isp.getJsonNode().path("mirroring").asInt() == 1);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_IMAGE_MIRROR, order = 161, icon = "fas fa-copyright", group = "VIDEO.ISP")
    public void setMirrorImage(boolean on) {
        setSetting(ReolinkCommand.SetIsp, isp -> isp.set(boolToInt(on), "mirroring"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_ANTI_FLICKER)
    public State getAntiFlicker() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : new StringType(isp.get("antiFlicker").asText());
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ANTI_FLICKER, order = 162, icon = "fab fa-flickr", group = "VIDEO.ISP")
    @UICameraSelectionAttributeValues(value = "IspRange", path = {"antiFlicker"})
    public void setAntiFlicker(String value) {
        setSetting(ReolinkCommand.SetIsp, isp -> isp.set(value, "antiFlicker"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_EXPOSURE)
    public State getExposure() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : new StringType(isp.get("exposure").asText());
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_EXPOSURE, order = 163, icon = "fas fa-sun", group = "VIDEO.ISP")
    @UICameraSelectionAttributeValues(value = "IspRange", path = {"exposure"})
    public void setExposure(String value) {
        setSetting(ReolinkCommand.SetIsp, isp -> isp.set(value, "exposure"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_DAY_NIGHT)
    public State getDayNight() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : new StringType(isp.getJsonNode().path("dayNight").asText());
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_DAY_NIGHT, order = 164, icon = "fas fa-cloud-sun", group = "VIDEO.ISP")
    @UICameraSelectionAttributeValues(value = "IspRange", path = {"dayNight"})
    public void setDayNight(String value) {
        setSetting(ReolinkCommand.SetIsp, isp -> isp.set(value, "dayNight"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_3DNR)
    public State get3DNR() {
        JsonType isp = (JsonType) getAttribute("Isp");
        return isp == null ? null : OnOffType.of(isp.getJsonNode().path("nr3d").asInt() == 1);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_3DNR, order = 165, icon = "fab fa-unity", group = "VIDEO.ISP")
    public void set3DNR(boolean on) {
        setSetting(ReolinkCommand.SetIsp, isp -> isp.set(boolToInt(on), "nr3d"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_RECORD_AUDIO)
    public State getRecAudio() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : OnOffType.of(enc.getJsonNode().path("audio").asInt() == 1);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_RECORD_AUDIO, order = 80, group = "VIDEO.ENC", subGroup = "VIDEO.mainStream",
                   subGroupIcon = "fas fa-dice-six")
    public void setRecAudio(boolean on) {
        setSetting(ReolinkCommand.SetEnc, enc -> enc.set(boolToInt(on), "audio"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_STREAM_MAIN_RESOLUTION)
    public State getStreamMainResolution() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("mainStream", "size").asText());
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_STREAM_MAIN_RESOLUTION, order = 81, group = "VIDEO.ENC", subGroup = "VIDEO.mainStream",
                   subGroupIcon = "fas " +
                       "fa-dice-six")
    @UIFieldSelection(value = SelectResolution.class, staticParameters = {"mainStream"})
    public void setStreamMainResolution(String value) {
        setSetting(ReolinkCommand.SetEnc, enc -> enc.set(value, "mainStream", "size"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_STREAM_MAIN_BITRATE)
    public State getStreamMainBitRate() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("mainStream", "bitRate").asText());
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_STREAM_MAIN_BITRATE, order = 82, group = "VIDEO.ENC", subGroup = "VIDEO.mainStream",
                   subGroupIcon = "fas " +
                       "fa-dice-six")
    @UIFieldSelection(value = SelectStreamValue.class, staticParameters = {"mainStream", "bitRate"})
    public void setStreamMainBitRate(String value) {
        setSetting(ReolinkCommand.SetEnc, enc -> enc.set(value, "mainStream", "bitRate"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_STREAM_MAIN_FRAMERATE)
    public State getStreamMainFrameRate() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("mainStream", "frameRate").asText());
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_STREAM_MAIN_FRAMERATE, order = 83, group = "VIDEO.ENC", subGroup = "VIDEO.mainStream",
                   subGroupIcon = "fas " +
                       "fa-dice-six")
    @UIFieldSelection(value = SelectStreamValue.class, staticParameters = {"mainStream", "frameRate"})
    public void setStreamMainFrameRate(String value) {
        setSetting(ReolinkCommand.SetEnc, enc -> enc.set(value, "mainStream", "frameRate"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_STREAM_MAIN_H264_PROFILE)
    public State getStreamMainH264Profile() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("mainStream", "profile").asText());
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_STREAM_MAIN_H264_PROFILE, order = 84, group = "VIDEO.ENC", subGroup = "VIDEO.mainStream",
                   subGroupIcon = "fas " +
                       "fa-dice-six")
    @UIFieldSelection(value = SelectStreamValue.class, staticParameters = {"mainStream", "profile"})
    public void setStreamMainH264Profile(String value) {
        setSetting(ReolinkCommand.SetEnc, enc -> enc.set(value, "mainStream", "profile"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_STREAM_SECONDARY_RESOLUTION)
    public State getStreamSecondaryResolution() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("subStream", "size").asText());
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_STREAM_SECONDARY_RESOLUTION, order = 90, group = "VIDEO.ENC", subGroup = "VIDEO.subStream",
                   subGroupIcon = "fas " +
                       "fa-dice-six")
    @UIFieldSelection(value = SelectResolution.class, staticParameters = {"subStream"})
    public void setStreamSecondaryResolution(String value) {
        setSetting(ReolinkCommand.SetEnc, enc -> enc.set(value, "subStream", "size"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_STREAM_SECONDARY_BITRATE)
    public State getStreamSecondaryBitRate() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("subStream", "bitRate").asText());
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_STREAM_SECONDARY_BITRATE, order = 91, group = "VIDEO.ENC", subGroup = "VIDEO.subStream",
                   subGroupIcon = "fas " +
                       "fa-dice-six")
    @UIFieldSelection(value = SelectStreamValue.class, staticParameters = {"subStream", "bitRate"})
    public void setStreamSecondaryBitRate(String value) {
        setSetting(ReolinkCommand.SetEnc, enc -> enc.set(value, "subStream", "bitRate"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_STREAM_SECONDARY_FRAMERATE)
    public State getStreamSecondaryFrameRate() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("subStream", "frameRate").asText());
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_STREAM_SECONDARY_FRAMERATE, order = 92, group = "VIDEO.ENC", subGroup = "VIDEO.subStream",
                   subGroupIcon = "fas " +
                       "fa-dice-six")
    @UIFieldSelection(value = SelectStreamValue.class, staticParameters = {"subStream", "frameRate"})
    public void setStreamSecondaryFrameRate(String value) {
        setSetting(ReolinkCommand.SetEnc, enc -> enc.set(value, "subStream", "frameRate"));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_STREAM_SECONDARY_H264_PROFILE)
    public State getStreamSecondaryH264Profile() {
        JsonType enc = (JsonType) getAttribute("Enc");
        return enc == null ? null : new StringType(enc.get("subStream", "profile").asText());
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_STREAM_SECONDARY_H264_PROFILE, order = 93, group = "VIDEO.ENC", subGroup = "VIDEO.subStream",
                   subGroupIcon = "fas" +
                       " fa-dice-six")
    @UIFieldSelection(value = SelectStreamValue.class, staticParameters = {"subStream", "profile"})
    public void setStreamSecondaryH264Profile(String value) {
        setSetting(ReolinkCommand.SetEnc, isp -> isp.set(value, "subStream", "profile"));
    }

    @Override
    public void initialize(EntityContext entityContext) {
        /*OnvifCameraService service = getService();
        if (StringUtils.isEmpty(service.getSnapshotUri())) {
            service.setSnapshotUri("/cgi-bin/api.cgi?cmd=Snap&channel=%s&rs=openHAB%token=".formatted(
                service.getEntity().getNvrChannel(), token));
        }*/
        runOncePerMinute(entityContext);
    }

    @Override
    public @Nullable String getSnapshotUri() {
        return "/cgi-bin/api.cgi?cmd=Snap&channel=%s&rs=homio&token=%s".formatted(
            service.getEntity().getNvrChannel(), token);
    }

    @Override
    public void pollCameraRunnable() {
       /* OnvifCameraService service = getService();
        if (getEntity().getNvrChannel() > 0) {
            service.sendHttpGET("/api.cgi?cmd=GetAiState&channel=" + getEntity().getNvrChannel() + "&user="
                + getEntity().getUser() + "&password=" + getEntity().getPassword().asString());
            service.sendHttpGET("/api.cgi?cmd=GetMdState&channel=" + getEntity().getNvrChannel() + "&user="
                + getEntity().getUser() + "&password=" + getEntity().getPassword().asString());
            return false;
        }
        return true;*/
    }

    @Override
    public void runOncePerMinute(EntityContext entityContext) {
        loginIfRequire();

        Root[] roots = firePost("", true,
            new ReolinkCmd(1, "GetIrLights", new ChannelParam()),
            new ReolinkCmd(1, "GetOsd", new ChannelParam()),
            new ReolinkCmd(1, "GetEnc", new ChannelParam()),
            new ReolinkCmd(1, "GetImage", new ChannelParam()),
            new ReolinkCmd(1, "GetIsp", new ChannelParam()));
        if (roots != null) {
            for (Root objectNode : roots) {
                String cmd = objectNode.cmd;
                switch (cmd) {
                    case "GetOsd" -> {
                        setAttribute("Osd", new JsonType(objectNode.value.osd));
                        setAttribute("OsdRange", new JsonType(objectNode.range.path("Osd")));
                    }
                    case "GetEnc" -> {
                        setAttribute("Enc", new JsonType(objectNode.value.enc));
                        setAttribute("EncRange", new JsonType(objectNode.range.path("Enc")));
                    }
                    case "GetImage" -> setAttribute("Img", new JsonType(objectNode.value.image));
                    case "GetIsp" -> {
                        setAttribute("Isp", new JsonType(objectNode.value.isp));
                        setAttribute("IspRange", new JsonType(objectNode.range.path("Isp")));
                    }
                    case "GetIrLights" -> setAttribute(IpCameraBindingConstants.CHANNEL_AUTO_LED,
                        OnOffType.of("Auto".equals(objectNode.value.irLights.path("state").asText())));
                }
            }
        }
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
        var requestEntity = RequestEntity.post(new URL(fullUrl).toURI()).contentType(MediaType.APPLICATION_JSON)
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
        if (osd.getJsonNode().path(path).path("enable").asInt() == 0) {
            return new StringType("");
        }
        return new StringType(osd.getJsonNode().path(path).path("pos").asText());
    }

    private void loginIfRequire() {
        if (this.tokenExpiration - System.currentTimeMillis() < 60000) {
            OnvifCameraEntity entity = getEntity();
            LoginRequest loginRequest = new LoginRequest(new LoginRequest.User(entity.getUser(), entity.getPassword().asString()));
            Root root = firePost("cmd=Login", false, new ReolinkCmd(0, "Login", loginRequest))[0];
            if (root.getError() != null) {
                throw new LoginFailedException(root.getError().toString());
            }
            this.tokenExpiration = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(root.value.token.leaseTime);
            this.token = root.value.token.name;
        }
    }

    @SneakyThrows
    private boolean firePostGetCode(ReolinkCommand command, boolean requireAuth, ReolinkCmd... commands) {
        Root root = firePost("cmd=" + command.name(), requireAuth, commands)[0];
        if (root == null) {
            return false;
        }
        if (root.getError() == null) {
            return true;
        }
        getEntityContext().ui().sendErrorMessage("Error while updating reolink settings. " + root.getError().getDetail());
        return false;
    }

    private void setSetting(ReolinkCommand command, Consumer<JsonType> updateHandler) {
        String key = command.name().substring(3);
        JsonType setting = (JsonType) getAttribute(key);
        if (setting == null) {
            return;
        }
        updateHandler.accept(setting);
        loginIfRequire();
        if (firePostGetCode(command, true,
            new ReolinkCmd(0, command.name(), new JSONObject().put(key, setting.toString())))) {
            getEntityContext().ui().sendSuccessMessage("Reolink " + command + " applied successfully");
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
        private final Object param;
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
    public static class Status {

        private int mon;
        private String table;
        private int year;
    }

    @Getter
    public static class SearchResult {

        @JsonProperty("Status")
        private List<Status> status;
        @JsonProperty("File")
        private List<File> file;
        private int channel;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Value {

        @JsonProperty("SearchResult")
        private SearchResult searchResult;
        @JsonProperty("Token")
        private Token token;
        @JsonProperty("IrLights")
        private ObjectNode irLights;
        @JsonProperty("Osd")
        private ObjectNode osd;
        @JsonProperty("Enc")
        private ObjectNode enc;
        @JsonProperty("Image")
        private ObjectNode image;
        @JsonProperty("Isp")
        private ObjectNode isp;
        @JsonProperty("rspCode")
        private int rspCode;
    }

    @Getter
    public static class Token {

        private int leaseTime;
        private String name;
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

    @Getter
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
        private Value value;
        private Error error;
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

    @Getter
    public static class SetIrLightsRequest {

        @JsonProperty("IrLights")
        private IrLights irLights = new IrLights();

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
                String size = enc.get(parameters.getStaticParameters()[0], "size").asText();
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
}
