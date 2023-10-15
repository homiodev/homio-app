package org.homio.addon.camera.onvif.impl;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_3DNR;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ANTI_FLICKER;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_AUTO_LED;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_BANDWIDTH;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_BGCOLOR;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_BLACK_LIGHT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_BLC;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_BLUE_GAIN;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_BRIGHT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_CONTRAST;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_CPU_LOADING;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_DATETIME_POSITION;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_DATETIME_SHOW;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_DAY_NIGHT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_DRC;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_AUDIO_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_EXPOSURE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_HDD;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_HUE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_IMAGE_MIRROR;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_IMAGE_ROTATE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_NAME_POSITION;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_NAME_SHOW;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_REBOOT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_RECORD_AUDIO;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_RED_GAIN;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_SATURATION;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_SHARPEN;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_STREAM_MAIN_BITRATE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_STREAM_MAIN_FRAMERATE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_STREAM_MAIN_H264_PROFILE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_STREAM_MAIN_RESOLUTION;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_STREAM_SECONDARY_BITRATE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_STREAM_SECONDARY_FRAMERATE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_STREAM_SECONDARY_H264_PROFILE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_STREAM_SECONDARY_RESOLUTION;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_WATERMARK_SHOW;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_WHITE_BALANCE;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.security.auth.login.LoginException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.homio.addon.camera.entity.CameraPlaybackStorage;
import org.homio.addon.camera.entity.IpCameraEntity;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.impl.ReolinkBrandHandler.SearchRequest.Search;
import org.homio.addon.camera.service.CameraDeviceEndpoint;
import org.homio.addon.camera.service.IpCameraService;
import org.homio.api.EntityContext;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.UpdatableValue;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.state.DecimalType;
import org.homio.api.state.JsonType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.field.action.HasDynamicUIFields;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.JsonUtils;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

@NoArgsConstructor
@CameraBrandHandler("Reolink")
public class ReolinkBrandHandler extends BaseOnvifCameraBrandHandler implements CameraPlaybackStorage {

    private static final JsonNode channelParam = OBJECT_MAPPER.createObjectNode().put("channel", 0);

    // TODO: for remove
    private static final RestTemplate restTemplate = new RestTemplate();

    private static final Map<String, EndpointConfiguration> configurations = new HashMap<>();

    private long tokenExpiration;
    private String token;
    // just detect if need update data
    private final UpdatableValue<String> cameraApiData = UpdatableValue.deferred("data", String.class);
    private final UpdatableValue<Boolean> fetchCameraPerformance = UpdatableValue.wrap(true, "reolink_fp");
    private final Map<String, State> attributes = new HashMap<>();

    public ReolinkBrandHandler(IpCameraService service) {
        super(service);
        fetchCameraPerformance.update(service.getEntity().getJsonData("reolink_fp", true));
    }

    @Override
    public void assembleUIFields(@NotNull HasDynamicUIFields.UIFieldBuilder uiFieldBuilder) {
        uiFieldBuilder.addSwitch(500, fetchCameraPerformance)
                      .inlineEdit(true).group("REOLINK", 100, "#2A86BF");
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        uiInputBuilder.addSelectableButton("VIDEO.FETCH_DATA_FROM_CAMERA", (entityContext, params) -> {
            fetchDataFromCamera();
            entityContext.ui().updateItem(service.getEntity());
            return ActionResponseModel.success();
        }).setIcon(new Icon("fas fa-cloud-arrow-down"));
        super.assembleActions(uiInputBuilder);
    }

    @Override
    public String getTitle() {
        return getEntity().getTitle();
    }

    static {
        // Exposure
        addConfig(new EndpointConfiguration(ENDPOINT_EXPOSURE, ConfigType.select, "Isp", "exposure"));

        // BlackLight
        addConfig(new EndpointConfiguration(ENDPOINT_BLACK_LIGHT, ConfigType.select, "Isp", "backLight")
            .setUpdateListener((state, handler) -> {
                String value = state.toString();
                boolean changed = handler.setEndpointVisible(ENDPOINT_BLC, "BackLightControl".equals(value));
                if (handler.setEndpointVisible(ENDPOINT_DRC, "DynamicRangeControl".equals(value))) {
                    changed = true;
                }
                if (changed) {
                    handler.getEntityContext().ui().updateItem(handler.getEntity());
                }
            }));
        addConfig(new EndpointConfiguration(ENDPOINT_BLC, ConfigType.number, "Isp", "blc"));
        addConfig(new EndpointConfiguration(ENDPOINT_DRC, ConfigType.number, "Isp", "drc"));

        addConfig(new EndpointConfiguration(ENDPOINT_DAY_NIGHT, ConfigType.select, "Isp", "dayNight"));
        addConfig(new EndpointConfiguration(ENDPOINT_WHITE_BALANCE, ConfigType.select, "Isp", "whiteBalance"));
        addConfig(new EndpointConfiguration(ENDPOINT_ANTI_FLICKER, ConfigType.select, "Isp", "antiFlicker"));
        addConfig(new EndpointConfiguration(ENDPOINT_IMAGE_ROTATE, ConfigType.bool, "Isp", "rotation"));
        addConfig(new EndpointConfiguration(ENDPOINT_3DNR, ConfigType.bool, "Isp", "nr3d"));
        addConfig(new EndpointConfiguration(ENDPOINT_IMAGE_MIRROR, ConfigType.bool, "Isp", "mirroring"));
        addConfig(new EndpointConfiguration(ENDPOINT_BLUE_GAIN, ConfigType.number, "Isp", "blueGain"));
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

        addConfig(new EndpointConfiguration(ENDPOINT_RECORD_AUDIO, ConfigType.bool, "Isp", "mainStream/audio"));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_MAIN_RESOLUTION, ConfigType.select, "Enc", "mainStream/size")
            .setRangeConverter(jsonNode -> createStreamSizeRange(jsonNode, "mainStream")));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_MAIN_BITRATE, ConfigType.select, "Enc", "mainStream/bitRate")
            .setRangeConverter(jsonNode -> jsonNode.get(0)));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_MAIN_FRAMERATE, ConfigType.select, "Enc", "mainStream/frameRate")
            .setRangeConverter(jsonNode -> jsonNode.get(0)));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_MAIN_H264_PROFILE, ConfigType.select, "Enc", "mainStream/profile")
            .setRangeConverter(jsonNode -> jsonNode.get(0)));

        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_SECONDARY_RESOLUTION, ConfigType.select, "Enc", "subStream/size")
            .setRangeConverter(jsonNode -> createStreamSizeRange(jsonNode, "subStream")));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_SECONDARY_BITRATE, ConfigType.select, "Enc", "subStream/bitRate")
            .setRangeConverter(jsonNode -> jsonNode.get(0)));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_SECONDARY_FRAMERATE, ConfigType.select, "Enc", "subStream/frameRate")
            .setRangeConverter(jsonNode -> jsonNode.get(0)));
        addConfig(new EndpointConfiguration(ENDPOINT_STREAM_SECONDARY_H264_PROFILE, ConfigType.select, "Enc", "subStream/profile")
            .setRangeConverter(jsonNode -> jsonNode.get(0)));

        addConfig(new EndpointConfiguration(ENDPOINT_CPU_LOADING, ConfigType.number, "Performance", "cpuUsed")
            .setWritable(false));
        addConfig(new EndpointConfiguration(ENDPOINT_BANDWIDTH, ConfigType.number, "Performance", "codecRate")
            .setWritable(false));
    }

    private static JsonNode createStreamSizeRange(JsonNode jsonNode, String path) {
        ArrayNode sizes = OBJECT_MAPPER.createArrayNode();
        for (JsonNode node : jsonNode) {
            sizes.add(JsonUtils.getJsonPath(node, path + "/size").asText());
        }
        return OBJECT_MAPPER.createObjectNode().set(path, OBJECT_MAPPER.createObjectNode().set("size", sizes));
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

    @Override
    public @Nullable String getSnapshotUri() {
        return "/cgi-bin/api.cgi?cmd=Snap&channel=%s&rs=homio".formatted(getEntity().getNvrChannel());
    }

    @Override
    public void postInitializeCamera(EntityContext entityContext) {
        service.urls.setUriConverter(uri -> {
            if (uri.startsWith("/cgi-bin")) {
                loginIfRequire();
                return "%s&token=%s".formatted(uri, token);
            }
            return uri;
        });

        service.addLowRequest(() -> {
            // fire fetch all info from camera every minute
            cameraApiData.getFreshValue(Duration.ofSeconds(60), () -> {
                fetchDataFromCamera();
                entityContext.ui().updateItem(getEntity());
                return "";
            });
            // fetch camera performance every 6 seconds
            if (fetchCameraPerformance.getValue()) {
                sendCameraRequest("GetPerformance", OBJECT_MAPPER.createObjectNode(), root -> {
                    JsonNode performance = root.value.path("Performance");
                    if (performance.has("cpuUsed")) {
                        service.getEndpoints().get(ENDPOINT_CPU_LOADING)
                               .setValue(new DecimalType(performance.get("cpuUsed").asInt()), true);
                    }
                    if (performance.has("codecRate")) {
                        service.getEndpoints().get(ENDPOINT_BANDWIDTH)
                               .setValue(new DecimalType(performance.get("codecRate").asInt()), true);
                    }
                });
            }
        });
    }

    public void fetchDataFromCamera() {
        loginIfRequire();

        Root[] roots = firePost("", true,
            new ReolinkCmd(1, "GetAbility", OBJECT_MAPPER.createObjectNode().set("User",
                OBJECT_MAPPER.createObjectNode().put("userName", getEntity().getUser()))),
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

                attributes.put(group, new JsonType(targetNode));
                if (objectNode.range != null && objectNode.range.has(group)) {
                    attributes.put(group + "Range", new JsonType(objectNode.range.path(group)));
                }
                /*
                if (objectNode.initial != null && objectNode.initial.has(group)) {
                    setAttribute(group + "Initial", new JsonType(objectNode.range.path(group)));
                }*/
                if (objectNode.range == null) {
                    objectNode.range = OBJECT_MAPPER.createObjectNode();
                }
                if (objectNode.initial == null) {
                    objectNode.initial = OBJECT_MAPPER.createObjectNode();
                }
                if (!customHandle(objectNode, cmd)) {
                    JsonNode rangeNode = objectNode.range.path(group);
                    JsonNode initialNode = objectNode.initial.path(group);
                    configurations.values().stream().filter(c -> c.group.equals(group)).forEach(config -> {
                        if (JsonUtils.hasJsonPath(targetNode, config.key)) {
                            CameraDeviceEndpoint endpoint = config.type.handler.handle(rangeNode, initialNode, config, this);
                            if (config.updateListener != null) {
                                endpoint.addChangeListener("reflect", state -> config.updateListener.accept(state, this));
                            }
                        }
                    });
                }
            }
            // Fire update listeners for all endpoints to be able to show/hide another endpoints
            for (EndpointConfiguration configuration : configurations.values()) {
                if (configuration.updateListener != null) {
                    getEndpoint(configuration.endpointId).ifPresent(endpoint ->
                        configuration.updateListener.accept(endpoint.getValue(), this));
                }
            }
        }
    }

    @Override
    public void onCameraConnected() {
        fetchDataFromCamera();
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
            case "GetAbility" -> {
                handleGetAbilityCommand(objectNode);
                yield true;
            }
            default -> false;
        };
    }

    private void handleGetAbilityCommand(Root objectNode) {
        JsonNode ability = objectNode.value.get("Ability");
        handleSwitch(ability, ENDPOINT_ENABLE_AUDIO_ALARM, "alarmAudio", "GetAudioAlarmV20", "SetAudioAlarmV20", "Audio");
        handleTrigger(ability, ENDPOINT_REBOOT, "reboot", new Icon("fas fa-power-off", Color.RED), "Reboot");
    }

    private void sendCameraRequest(String cmd, JsonNode param, boolean resend, Consumer<Root> handler, Consumer<Exception> errorHandler) {
        HttpRequest httpRequest = Curl.createPostRequest(getAuthUrl("cmd=" + cmd, true),
            Set.of(new ReolinkCmd(0, cmd, param)));
        Curl.sendAsync(httpRequest, Root[].class, (roots, status) -> {
            Root root = roots.length == 0 ? null : roots[0];
            try {
                testForErrors(root);
                handler.accept(root);
            } catch (LoginException le) {
                if (!resend) { // already sent twice
                    this.tokenExpiration = 0;
                    sendCameraRequest(cmd, param, true, handler, errorHandler);
                } else {
                    errorHandler.accept(le);
                }
            } catch (Exception ex) {
                errorHandler.accept(ex);
            }
        });
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
        service.addEndpoint(ENDPOINT_HDD, key -> {
            CameraDeviceEndpoint endpoint = new CameraDeviceEndpoint(getEntity(), getEntityContext(), key, DeviceEndpoint.EndpointType.string, false);
            endpoint.setValueConverter(state -> {
                JsonNode hddInfo = ((JsonType) state).getJsonNode();
                return new StringType(hddInfo.get("size").asText() + "/" + hddInfo.get("capacity").asText());
            });
            return endpoint;
        }, state -> {
        }).setValue(new JsonType(objectNode.value.withArray("HddInfo").path(0)));
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
        service.addEndpointSwitch(ENDPOINT_AUTO_LED, state ->
            sendCameraPushRequest("SetIrLights", OBJECT_MAPPER.createObjectNode().set("IrLights",
                OBJECT_MAPPER.createObjectNode().put("state", state.boolValue("Auto", "Off")))))
            .setValue(OnOffType.of("auto".equals(objectNode.value.get("IrLights").get("state").asText())));
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
        try {
            testForErrors(roots.length == 0 ? null : roots[0]);
        } catch (LoginException le) {
            this.tokenExpiration = 0;
            return firePost(url, requireAuth, commands);
        }
        return roots;
    }

    public String getAuthUrl(String url, boolean requireAuth) {
        if (requireAuth) {
            url = updateURL(url);
        }
        return "http://" + getEntity().getIp() + ":" + getEntity().getRestPort() + "/cgi-bin/api.cgi?" + url;
    }

    private void loginIfRequire() {
        if (this.tokenExpiration - System.currentTimeMillis() < 60000) {
            IpCameraEntity entity = getEntity();
            LoginRequest request = new LoginRequest(new LoginRequest.User(entity.getUser(), entity.getPassword().asString()));
            Root root = firePost("cmd=Login", false, new ReolinkCmd(0, "Login", OBJECT_MAPPER.valueToTree(request)))[0];
            if (root.getError() != null) {
                throw new ServerException(root.getError().toString()).setStatus(HttpStatus.CONFLICT);
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
        getEntityContext().ui().toastr().error("Error while updating reolink settings. " + root.getError().getDetail());
        return false;
    }

    /*private void setSetting(String endpointID, Consumer<JsonType> updateHandler) {
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
            getEntityContext().ui().toastr().success("Reolink '" + configuration.key + "' changed  successfully");
        }
    }*/

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
            getEntityContext().ui().toastr().success("Reolink '" + configuration.key + "' changed  successfully");
        }
    }

    enum ReolinkCommand {
        SetOsd, SetIsp, SetEnc, SetIrLights
    }

    private void handleSwitch(JsonNode ability, String endpointKey, String abilityKey, String getKey, String setKey, String valueKey) {
        int permit = ability.path(abilityKey).path("permit").asInt(0);
        if (permit > 0) {
            CameraDeviceEndpoint endpoint = service.addEndpointSwitch(endpointKey, state ->
                sendCameraPushRequest(setKey, OBJECT_MAPPER.createObjectNode().set(valueKey,
                    OBJECT_MAPPER.createObjectNode().put("enable", 1))));
            sendCameraRequest(getKey, channelParam, root -> {
                boolean value = root.value.path(valueKey).path("enable").asBoolean(false);
                endpoint.setValue(OnOffType.of(value), true);
            });
        }
    }

    private void handleTrigger(JsonNode ability, String endpointKey, String abilityKey, Icon icon, String cmd) {
        int permit = ability.path(abilityKey).path("permit").asInt(0);
        if (permit > 0) {
            service.addEndpointTrigger(endpointKey, icon, null, "W.CONFIRM." + cmd.toUpperCase(), Color.ERROR_DIALOG, state ->
                sendCameraPushRequest(cmd, OBJECT_MAPPER.createObjectNode()));
        }
    }

    private void sendCameraPushRequest(String cmd, JsonNode param) {
        sendCameraRequest(cmd, param, root -> {
            try {
                testForErrors(root);
                getEntityContext().ui().toastr().success("Reolink set " + cmd + " applied successfully");
            } catch (Exception ex) {
                log.error("[{}]: Cmd: {}. Error: {}", entityID, cmd, ex.getMessage());
                getEntityContext().ui().toastr().error("Error while updating reolink param. " + ex.getMessage());
            }
        });
    }

    private static void testForErrors(Root root) throws LoginException {
        if (root == null) {
            throw new IllegalStateException("Unknown error");
        }
        if (root.error != null) {
            if (root.error.rspCode == -6) {
                throw new LoginException();
            }
            throw new IllegalStateException(root.error.toString());
        }
        if (root.value == null) {
            throw new IllegalStateException("Unknown error");
        }
        int rspCode = root.value.path("rspCode").asInt();
        if (rspCode != 200 && rspCode != 0) {
            throw new IllegalStateException("rspCode: " + rspCode);
        }
    }

    private void sendCameraRequest(String cmd, JsonNode param, Consumer<Root> handler) {
        sendCameraRequest(cmd, param, false, handler, error ->
            log.error("[{}]: Cmd request: {}. Error response from camera: {}", entityID, cmd, error));
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
        number((rangeNode, initialNode, c, handler) -> {
            Consumer<State> setter = state -> handler.setSetting(c.endpointId, state);
            Supplier<State> getter = () -> {
                JsonType group = (JsonType) handler.getAttribute(c.group);
                return group == null ? null : new DecimalType(group.getJsonNode().path(c.key).asInt());
            };
            JsonNode numConfigPath = JsonUtils.getJsonPath(rangeNode, c.key);
            Float min = numConfigPath.has("min") ? (float)numConfigPath.path("min").asInt() : null;
            Float max = numConfigPath.has("max") ? (float)numConfigPath.path("max").asInt() : null;
            CameraDeviceEndpoint endpoint = handler.service.addEndpointSlider(c.endpointId, min, max, setter, c.writable);
            endpoint.setValue(getter.get(), true);
            JsonNode initPath = JsonUtils.getJsonPath(initialNode, c.key);
            if (!initPath.isMissingNode()) {
                float defaultValue = initPath.floatValue();
                endpoint.setDefaultValue(defaultValue);
            }
            return endpoint;
        }),
        bool((rangeNode, initialNode, c, handler) -> {
            Consumer<State> setter = state -> handler.setSetting(c.endpointId, state);
            Supplier<State> getter = () -> {
                JsonType group = (JsonType) handler.getAttribute(c.group);
                return group == null ? null : OnOffType.of(JsonUtils.getJsonPath(group.getJsonNode(), c.key).asInt() == 1);
            };
            CameraDeviceEndpoint endpoint = handler.service.addEndpointSwitch(c.endpointId, setter, c.writable);
            endpoint.setValue(getter.get(), true);
            return endpoint;
        }),
        select((rangeNode, initialNode, c, handler) -> {
            Set<String> range = new LinkedHashSet<>();
            if (c.rangeConverter != null) {
                rangeNode = c.rangeConverter.apply(rangeNode);
            }
            JsonUtils.getJsonPath(rangeNode, c.key).forEach(jsonNode -> range.add(jsonNode.asText()));
            Consumer<State> setter = state -> handler.setSetting(c.endpointId, state);
            Supplier<State> getter = () -> {
                JsonType group = (JsonType) handler.getAttribute(c.group);
                return group == null ? null : new StringType(JsonUtils.getJsonPath(group.getJsonNode(), c.key).asText());
            };
            CameraDeviceEndpoint endpoint = handler.service.addEndpointEnum(c.endpointId, range, setter);
            endpoint.setValue(getter.get(), true);
            JsonNode initPath = JsonUtils.getJsonPath(initialNode, c.key);
            if (!initPath.isMissingNode()) {
                String defaultValue = initPath.toString();
                if (defaultValue.startsWith("\"") && defaultValue.endsWith("\"")) {
                    defaultValue = defaultValue.substring(1, defaultValue.length() - 1);
                }
                endpoint.setDefaultValue(defaultValue);
            }
            return endpoint;
        });
        private final ConfigTypeHandler handler;
    }

    private interface ConfigTypeHandler {

        CameraDeviceEndpoint handle(JsonNode rangeNode, JsonNode initialNode, EndpointConfiguration c, ReolinkBrandHandler handler);
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
        @NoArgsConstructor
        @AllArgsConstructor
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
        private @Setter boolean writable = true;
        private @Setter Function<JsonNode, JsonNode> rangeConverter;
        private @Setter BiConsumer<State, BaseOnvifCameraBrandHandler> updateListener;

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
