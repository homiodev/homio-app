package org.homio.addon.ibkr;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;
import java.util.function.BiConsumer;

import static java.lang.Double.parseDouble;

@Getter
@AllArgsConstructor
public enum TickType {
    LAST_PRICE("31", "lastPrice", (position, s) -> position.setLastPrice(parseDouble(s))),
    HIGH("70", "curDayHighPrice", (position, s) -> position.setCurDayHighPrice(parseDouble(s))),
    LOW("71", "curDayLowPrice", (position, s) -> position.setCurDayLowPrice(parseDouble(s))),
    MARKET_VALUE("73", "marketValue", (position, s) -> position.setMktValue(parseDouble(s))),
    AVG_PRICE("74", "avgPrice", (position, s) -> position.setAvgPrice(parseDouble(s))),
    UNREALIZED_PNL("75", "unrealizedPnl", (position, s) -> position.setUnrealizedPnl(parseDouble(s))),
    CHANGE_PRICE("82", "changePrice", (position, s) -> position.setChangePrice(parseDouble(s))),
    CHANGE_PERCENT("83", "changePercent", (position, s) -> position.setChangePercent(parseDouble(s))),
    BID("84", "bidPrice", (position, s) -> position.setBidPrice(parseDouble(s))),
    ASK_SIZE("85", "askSize", (position, s) -> position.setAskSize(parseDouble(s))),
    BID_SIZE("88", "bidSize", (position, s) -> position.setBidSize(parseDouble(s))),
    HistVolatility("7087", "histVolatility", IbkrApi.Position::setHistVolatility),
    PUT_CALL_RATIO("7285", "putCallRatio", IbkrApi.Position::setPutCallRatio),
    WEEKS_52_LOW("7293", "52weekLow", (position, s) -> position.setWeeks52Low(parseDouble(s))),
    WEEKS_52_HIGH("7294", "52weekHigh", (position, s) -> position.setWeeks52High(parseDouble(s))),
    OPEN_PRICE("7295", "openPrice", (position, s) -> position.setOpenPrice(parseDouble(s))),
    DIV_AMOUNT("7286", "divAmount", (position, divAmount) -> position.setDivAmount(parseDouble(divAmount))),
    PE("7290", "P/E", (position, pe) -> position.setPe(parseDouble(pe))),
    EPS("7291", "EPS", (position, eps) -> position.setEps(parseDouble(eps))),
    TODAY_CLOSE_PRICE("7296", "todayClosePrice", (position, s) -> position.setTodayClosePrice(parseDouble(s))),
    PRIOR_CLOSE_PRICE("7741", "priorClosePrice", (position, s) -> position.setPriorClosePrice(parseDouble(s)));

    private final String mNdx;
    private final String mField;
    private final BiConsumer<IbkrApi.Position, String> handler;

    public static TickType get(String mNdx) {
        TickType[] values = values();
        for (TickType tickType : values) {
            if (Objects.equals(tickType.mNdx, mNdx)) {
                return tickType;
            }
        }

        return null;
    }

    public static TickType getByField(String mField) {
        TickType[] values = values();
        for (TickType tickType : values) {
            if (Objects.equals(tickType.mField, mField)) {
                return tickType;
            }
        }

        return null;
    }
}