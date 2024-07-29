package org.homio.addon.ibkr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.JSDisableMethod;
import org.homio.hquery.Curl;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Log4j2
public class IbkrApi {

    private final Map<Long, Consumer<JSONObject>> subscriptions = new HashMap<>();
    private final IbkrEntity entity;
    private final WebSocketClient ws;
    private String sessionID;
    @Getter
    private @NotNull List<Position> positions = List.of();
    @Getter
    private @NotNull List<Order> orders = List.of();

    private final LoadingCache<String, JsonNode> fifteenMinQueryCache;
    private long lastWidgetInfoRequest;
    private long lastPositionsRequest;
    private long lastOrdersRequest;
    private Collection<Position> widgetInfo = List.of();

    @SneakyThrows
    public IbkrApi(IbkrEntity entity, Context context) {
        this.entity = entity;
        this.fifteenMinQueryCache = CacheBuilder.newBuilder().
                expireAfterWrite(15, TimeUnit.MINUTES).build(new CacheLoader<>() {
                    public @NotNull JsonNode load(@NotNull String url) {
                        return Curl.get(entity.getUrl(url), JsonNode.class);
                    }
                });

        if (entity.getAccountId().isEmpty()) {
            ObjectNode accounts = Curl.get(entity.getUrl("portfolio/account"), ObjectNode.class);
            if (!accounts.isEmpty()) {
                JsonNode account = accounts.get(0);
                entity.setJsonData("aid", account.get("accountId").asText());
                entity.setName(account.get("desc").asText());
                context.db().save(entity);
            }
        }
        if (this.sessionID == null) {
            JsonNode tickleResult = Curl.post(entity.getUrl("/tickle"), null, JsonNode.class);
            this.sessionID = tickleResult.get("session").asText();
        }
        ws = new WebSocketClient(new URI("ws://localhost:" + entity.getPort() + "/v1/api/ws")) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                log.info("IBKR web-socket connected");

            }

            @Override
            public void onMessage(String s) {
                log.info(s);
            }

            @SneakyThrows
            @Override
            public void onMessage(ByteBuffer bytes) {
                JSONObject json = new JSONObject(bytes.array());
                String topic = json.optString("topic");
                long conid = json.optLong("conid");
                if (topic.isEmpty() || conid == 0L) {
                    return;
                }
                var subs = subscriptions.entrySet().stream().filter(e -> e.getKey() == conid).map(Map.Entry::getValue).toList();
                subs.forEach(e -> {
                    if (json.optBoolean("success")) {
                        log.info("Success subscribed on: {}", json);
                    } else {
                        subscriptions.values().forEach(h -> h.accept(json));
                    }
                });
            }

            @Override
            public void onClose(int i, String s, boolean b) {

            }

            @Override
            public void onError(Exception e) {
                log.error(e);
            }
        };
        ws.connect();
                    /*context.bgp().builder("ibkr-keep-alive").intervalWithDelay(Duration.ofSeconds(60)).execute(() -> {
                        Curl.post(entity.getUrl("/tickle"), null, JsonNode.class);
                        this.ws.send("tic");
                    });*/

        List<String> requestFields = Arrays.stream(TickType.values()).map(TickType::getMNdx).toList();
        for (Position position : getAllPositions()) {
            subscribe(position.conid, new PositionRequest(requestFields), json -> {
                log.info("Update market data {}", json);

                for (String key : json.keySet()) {
                    String value = json.getString(key);
                    TickType tickType = TickType.get(key);
                    if (tickType != null) {
                        tickType.getHandler().accept(position, value);
                    }
                }
            });
        }
    }

    @SneakyThrows
    private void subscribe(long conId, @NotNull Object args, @NotNull Consumer<JSONObject> handler) {
        String message = "smd+" + conId + "+" + OBJECT_MAPPER.writeValueAsString(args);
        this.subscriptions.put(conId, handler);
        this.ws.send(message);
    }

    @SneakyThrows
    private @NotNull List<Position> getAllPositions() {
        if (System.currentTimeMillis() - lastPositionsRequest > 60_000) {
            lastPositionsRequest = System.currentTimeMillis();

            if (positions.isEmpty()) {
                List<Position> result = new ArrayList<>();
                String baseUrl = "portfolio/%s/positions/".formatted(entity.getAccountId());
                for (int i = 0; i < 100; i++) {
                    List<Position> list = restTemplate.exchange(entity.getUrl(baseUrl + i),
                                    HttpMethod.GET, null, new ParameterizedTypeReference<List<Position>>() {
                                    })
                            .getBody();
                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    result.addAll(list);
                }
                positions = result;
            }
        }
        return positions;
    }

    @SneakyThrows
    public String getPerformance() {
        String url = entity.getUrl("pa/performance");
        JsonNode response = fifteenMinQueryCache.getIfPresent("pa/performance");
        if (response == null) {
            fifteenMinQueryCache.put("pa/performance", Curl.post(url,
                    new PerformanceRequest(Set.of(entity.getAccountId())), ObjectNode.class));
            response = fifteenMinQueryCache.get("pa/performance");
        }
        return response.get("data").asText();
    }

    public Collection<Position> getAllWidgetInfo() {
        if (System.currentTimeMillis() - lastWidgetInfoRequest > 60000) {
            lastWidgetInfoRequest = System.currentTimeMillis();
            Map<String, Position> tickerToPosition = getAllPositions().stream()
                    .collect(Collectors.toMap(Position::getTicker, e -> e));
            for (Order order : getAllOrders()) {
                tickerToPosition.computeIfAbsent(order.ticker, t ->
                                new Position()
                                        .setTicker(order.ticker)
                                        .setAcctId(order.account)
                                        .setConid(order.conid)
                                        .setCurrency(order.cashCcy)
                                        .setName(order.companyName)
                                        .setListingExchange(order.listingExchange))
                        .orders.put(order.orderId, order);
            }
            widgetInfo = tickerToPosition.values();
        }
        return widgetInfo;
    }

    @SneakyThrows
    private @NotNull List<Order> getAllOrders() {
        if (System.currentTimeMillis() - lastOrdersRequest > 60_000 || orders.isEmpty()) {
            lastOrdersRequest = System.currentTimeMillis();
            OrderResponse response = restTemplate.getForObject(entity.getUrl("iserver/account/orders"), OrderResponse.class);
            orders = response == null ? List.of() : response.orders;
        }
        return orders;
    }

    @SneakyThrows
    @JSDisableMethod
    public double getSummary(String field) {
        JsonNode response = fifteenMinQueryCache.get("portfolio/%s/summary".formatted(entity.getAccountId()));
        return response.get(field).get("amount").asDouble();
    }

    public double getTotalCash() {
        return getSummary("totalcashvalue");
    }

    public double getAvailableFunds() {
        return getSummary("availablefunds");
    }

    public void destroy() {
        for (Long conId : subscriptions.keySet()) {
            this.subscriptions.remove(conId);
            this.ws.send("umd+" + conId + "+{}");
        }
    }

    @Getter
    @Setter
    public static class SubscribeFields {
        private List<Integer> fields;
        private boolean snapshot = true;
        private int tempo = 2000;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class Position {
        private String acctId;
        private long conid;
        private String contractDesc;
        private double position;
        private double mktPrice;
        private double mktValue;
        private String currency;
        private double avgCost;
        private double avgPrice;
        private double realizedPnl;
        private double unrealizedPnl;
        private String listingExchange;
        private String countryCode;
        private String name;
        private String sector;
        private String ticker;
        private String fullName;
        private String sectorGroup;
        private String group;

        private Double bidSize;
        private Double askSize;
        private Double bidPrice;
        private Double lastPrice;
        private Double todayClosePrice;
        private Double priorClosePrice;
        private Double openPrice;
        private Double changePrice;
        private Double changePercent;
        private Double curDayHighPrice;
        private Double curDayLowPrice;
        private Double weeks52Low;
        private Double weeks52High;
        private String putCallRatio;
        private String histVolatility;
        private Double divAmount;
        private Double pe;
        private Double eps;

        @JsonIgnore
        private final Map<Long, Order> orders = new HashMap<>();

        public String getTicker() {
            return StringUtils.defaultIfEmpty(ticker, contractDesc);
        }
    }

    @Getter
    @Setter
    public static class OrderResponse {
        private List<Order> orders;
    }

    @Getter
    @Setter
    public static class Order {
        private long conid;
        private String account;
        private long orderId;
        private String cashCcy;
        private String sizeAndFills;
        private String orderDesc;
        private String ticker;
        private String listingExchange;
        private double remainingQuantity;
        private double filledQuantity;
        private double totalSize;
        private String companyName;
        private String status;
        private boolean outsideRTH;
        private String orderType;
        private String price;
        private String side;
    }

    @Getter
    @AllArgsConstructor
    private static class PerformanceRequest {
        private final String freq = "D";
        private final Set<String> acctIds;
    }

    @Getter
    @RequiredArgsConstructor
    private static class PositionRequest {
        private final List<String> fields;
        private final int tempo = 2000;
        private final boolean snapshot = true;
    }

    private static final RestTemplate restTemplate;

    static {
        restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }
}
