package org.homio.addon.ibkr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pivovarit.function.ThrowingSupplier;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.JSDisableMethod;
import org.homio.api.cache.CachedValue;
import org.homio.api.util.CommonUtils;
import org.homio.hquery.Curl;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Log4j2
public class IbkrApi {

    private final Set<Long> subscriptions = new HashSet<>();
    private final IbkrEntity entity;
    private final IbkrService service;
    private final List<String> requestFields;
    private final CachedValue<Map<String, Object>, Object> summary = new CachedValue<>(Duration.ofSeconds(60), new ThrowingSupplier<>() {
        @Override
        public Map<String, Object> get() {
            try {
                JsonNode summary = Curl.get(entity.getUrl("portfolio/%s/summary".formatted(entity.getAccountId())), JsonNode.class);
                Map<String, Object> values = new HashMap<>();
                summary.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode valueNode = entry.getValue();
                    Double value = valueNode.path("value").isNull() ?
                            valueNode.path("amount").asDouble() : valueNode.get("value").asDouble();
                    values.put(key, value);
                });
                return values;
            } catch (Exception ignored) {
            }
            return Map.of();
        }
    });
    private final CachedValue<JsonNode, Object> performance = new CachedValue<>(Duration.ofMinutes(10), new ThrowingSupplier<>() {
        @Override
        public JsonNode get() {
            try {
                String url = entity.getUrl("pa/performance");
                var body = OBJECT_MAPPER.writeValueAsString(new PerformanceRequest(Set.of(entity.getAccountId())));
                return Curl.post(url, body, JsonNode.class);
            } catch (Exception ignored) {
            }
            return OBJECT_MAPPER.createObjectNode();
        }
    });
    private String sessionID;
    private WebSocketClient ws;
    private JSONObject profitAndLoss;
    private JSONObject trades;

    @Getter
    private Collection<Position> allPosition = List.of();

    @SneakyThrows
    public IbkrApi(IbkrEntity entity, IbkrService service) {
        this.entity = entity;
        this.service = service;
        this.requestFields = Arrays.stream(TickType.values()).map(TickType::getMNdx).toList();
    }

    void init(Context context) throws URISyntaxException {
        if (entity.getAccountId().isEmpty()) {
            log.info("Fetching portfolio account");
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
                ws.send("spl+{}");
                ws.send("str+{}");
            }

            @Override
            public void onMessage(String message) {
                log.debug(message);
            }

            @SneakyThrows
            @Override
            public void onMessage(ByteBuffer bytes) {
                JSONObject json = new JSONObject(new String(bytes.array()));
                String topic = json.optString("topic");
                long conid = json.optLong("conid");
                if (topic.isEmpty() || conid == 0L) {
                    return;
                }
                if (topic.equals("spl")) {
                    profitAndLoss = json;
                }
                if (topic.equals("blt")) {
                    String message = json.optJSONObject("args").optString("message");
                    log.warn("Bulletins message: {}", message);
                    context.ui().toastr().warn("IBKR", message);
                }
                if (topic.equals("str")) {
                    trades = json;
                }
                if (json.optBoolean("success")) {
                    log.info("Success subscribed on: {}", json);
                    return;
                }
                Position position = allPosition.stream().filter(p -> p.getConid() == conid).findFirst().orElse(null);
                if (position == null) {
                    return;
                }
                log.debug("Update market data {}", json);
                for (String key : json.keySet()) {
                    Object value = json.opt(key);
                    if (value != null && !value.toString().isEmpty()) {
                        TickType tickType = TickType.get(key);
                        if (tickType != null) {
                            tickType.getHandler().accept(position, value.toString());
                        }
                    }
                }

                service.setDataToUI();
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                log.warn("IBKR close websocket connection");
                try {
                    Thread.sleep(5000);
                    this.reconnectBlocking();
                } catch (Exception ex) {
                    log.error("Unable to reconnect websocket connection", ex);
                }
            }

            @Override
            public void onError(Exception e) {
                log.error("IBKR error websocket", e);
            }
        };
        ws.connect();

        context.bgp().builder("ibkr-fetch-positions")
                .interval(Duration.ofSeconds(60))
                .execute(() -> {
                    Curl.post(entity.getUrl("/tickle"), null, JsonNode.class);
                    try {
                        this.ws.send("tic");
                    } catch (Exception ex) {
                        log.error("Error send tic to websocket: {}", CommonUtils.getErrorMessage(ex));
                    }

                    List<Position> positions = new ArrayList<>();
                    String baseUrl = "portfolio/%s/positions/".formatted(entity.getAccountId());
                    for (int i = 0; i < 100; i++) {
                        List<Position> list = null;
                        try {
                            list = Curl.getList(entity.getUrl(baseUrl + i), Position.class);
                        } catch (Exception ignored) {
                        }
                        if (list == null || list.isEmpty()) {
                            break;
                        }
                        positions.addAll(list);
                    }
                    Map<Long, Position> tickerToPosition = positions.stream()
                            .collect(Collectors.toMap(Position::getConid, e -> e));

                    List<Order> orders = List.of();
                    try {
                        OrderResponse response = Curl.get(entity.getUrl("iserver/account/orders"), OrderResponse.class);
                        if (response != null) {
                            orders = response.orders;
                        }
                    } catch (Exception ignored) {
                    }

                    for (Order order : orders) {
                        tickerToPosition.computeIfAbsent(order.conid, t ->
                                        new Position()
                                                .setTicker(order.ticker)
                                                .setAcctId(order.account)
                                                .setConid(order.conid)
                                                .setCurrency(order.cashCcy)
                                                .setName(order.companyName)
                                                .setListingExchange(order.listingExchange))
                                .orders.put(order.orderId, order);
                    }

                    subscriptions.removeIf(conid -> {
                        if (!tickerToPosition.containsKey(conid)) {
                            log.info("Unsubscribe from position {}", conid);
                            ws.send("umd+" + conid + "+{}");
                            return true;
                        }
                        return false;
                    });

                    for (Position position : tickerToPosition.values()) {
                        if (!subscriptions.contains(position.getConid())) {
                            log.info("Subscribe to position {}", position.getTicker());
                            subscribe(position.getConid(), new PositionRequest(requestFields));
                        }
                    }

                    allPosition = tickerToPosition.values();
                });
    }

    @SneakyThrows
    private void subscribe(long conId, @NotNull Object args) {
        String message = "smd+" + conId + "+" + OBJECT_MAPPER.writeValueAsString(args);
        this.subscriptions.add(conId);
        this.ws.send(message);
    }

    /*private void fetchNewPositionPrices(Map<String, Position> tickerToPosition) {
        String contracts = tickerToPosition.values().stream()
                .filter(p -> p.getLastPrice() == null)
                .map(p -> String.valueOf(p.getConid()))
                .collect(Collectors.joining(","));

        ArrayNode values = Curl.get(entity.getUrl("iserver/marketdata/snapshot?conids=" + contracts + "&fields=" + requestFields), ArrayNode.class);
        for (JsonNode contractInfo : values) {
            int conid = contractInfo.get("conid").asInt();
            Position position = tickerToPosition.values().stream().filter(p -> p.conid == conid).findAny().orElse(null);
            if (position != null) {
                for (Iterator<String> iterator = contractInfo.fieldNames(); iterator.hasNext(); ) {
                    String key = iterator.next();
                    TickType tickType = TickType.get(key);
                    if (tickType != null) {
                        String value = contractInfo.get(key).asText();
                        tickType.getHandler().accept(position, value);
                    }
                    contractInfo.get(key).asText();
                }
            }
        }
    }*/

    @SneakyThrows
    @JSDisableMethod
    public double getSummary(String field) {
        return (double) summary.getValue().getOrDefault(field, 0D);
    }

    public double getTotalCash() {
        return getSummary("totalcashvalue");
    }

    public double getEquityWithLoanValue() {
        return getSummary("equitywithloanvalue");
    }

    public double getNetLiquidation() {
        return getSummary("netliquidation");
    }

    public double getPreviousDayEquityWithLoanValue() {
        return getSummary("previousdayequitywithloanvalue");
    }

    public void destroy(@NotNull Context context) {
        context.bgp().cancelThread("ibkr-fetch-positions");
        if (ws != null) {
            for (long conId : subscriptions) {
                ws.send("umd+" + conId + "+{}");
            }
            this.ws.send("upl");
            this.ws.send("utr");
            allPosition = List.of();
            subscriptions.clear();
            ws.close();
            ws = null;
        }
    }

    public WidgetInfo getWidgetInfo() {
        return new WidgetInfo(
                allPosition.stream().map(TickerInfo::new).toList(),
                performance.getValue(),
                entity.getJsonData("showSummary", false) ? summary.getValue() : null,
                profitAndLoss,
                entity.getJsonData("showTrades", false) ? trades : null);
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
        @JsonIgnore
        private final Map<Long, Order> orders = new HashMap<>();
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
        private double unrealizedPnlPercent;
        private String listingExchange;
        private String countryCode;
        private String name;
        private String sector;
        private String ticker;
        private String fullName;
        private String sectorGroup;
        private String group;
        // From ws only
        private Double bidPrice;
        private Double askPrice;
        private Double volume;
        private Double lastPrice;
        private Double todayClosePrice;
        private Double priorClosePrice;
        private Double openPrice;
        private Double changePrice;
        private Double divAmount;
        private Double pe;
        private Double eps;
        private Double weeks52Low;
        private Double weeks52High;
        private String putCallRatio;
        private Double changePercent;
        private Double curDayHighPrice;
        private Double curDayLowPrice;
        private Double high52;
        private Double low52;

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

    private record PositionRequest(List<String> fields) {
    }

    public record WidgetInfo(List<TickerInfo> tickers,
                             JsonNode performance,
                             Map<String, Object> summary,
                             JSONObject profitAndLoss,
                             JSONObject trades) {
    }

    @Getter
    public static class TickerInfo {
        private final List<TickerOrder> orders;
        private final IbkrApi.Position position;

        public TickerInfo(IbkrApi.Position position) {
            this.position = position;
            this.orders = position.getOrders().values().stream().map(TickerInfo::getOrderInfo).toList();
        }

        private static TickerOrder getOrderInfo(IbkrApi.Order order) {
            return new TickerOrder(order.getSide().equals("SELL") ? "Sell" : "Buy",
                    order.getTotalSize(),
                    order.getOrderType(),
                    order.getPrice(),
                    order.isOutsideRTH());
        }

        private record TickerOrder(String side, double size, String type, String price, boolean outsideRTH) {
        }
    }
}
