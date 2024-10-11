package org.homio.addon.ibkr;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

@Getter
@AllArgsConstructor
public enum TickType {
    LAST_PRICE("31", "lastPrice", (position, s) -> position.setLastPrice(safeParseDouble(s))),
    HIGH("70", "curDayHighPrice", (position, s) -> position.setCurDayHighPrice(safeParseDouble(s))),
    LOW("71", "curDayLowPrice", (position, s) -> position.setCurDayLowPrice(safeParseDouble(s))),
    MARKET_VALUE("73", "marketValue", (position, s) -> position.setMktValue(safeParseDouble(s))),
    AVG_PRICE("74", "avgPrice", (position, s) -> position.setAvgPrice(safeParseDouble(s))),
    UNREALIZED_PNL("75", "unrealizedPnl", (position, s) -> position.setUnrealizedPnl(safeParseDouble(s))),
    UNREALIZED_PNL_PERCENT("80", "unrealizedPnlPercent", (position, s) -> position.setUnrealizedPnlPercent(safeParseDouble(s))),
    CHANGE_PRICE("82", "changePrice", (position, s) -> position.setChangePrice(safeParseDouble(s))),
    CHANGE_PERCENT("83", "changePercent", (position, s) -> position.setChangePercent(safeParseDouble(s))),
    BID("84", "bidPrice", (position, s) -> position.setBidPrice(safeParseDouble(s))),
    ASK("86", "askPrice", (position, s) -> position.setAskPrice(safeParseDouble(s))),
    VOLUME("87", "volume", (position, s) -> position.setVolume(safeParseDouble(s))),
    PUT_CALL_RATIO("7285", "putCallRatio", IbkrApi.Position::setPutCallRatio),
    WEEKS_52_LOW("7293", "52weekLow", (position, s) -> position.setWeeks52Low(safeParseDouble(s))),
    WEEKS_52_HIGH("7294", "52weekHigh", (position, s) -> position.setWeeks52High(safeParseDouble(s))),
    OPEN_PRICE("7295", "openPrice", (position, s) -> position.setOpenPrice(safeParseDouble(s))),
    DIV_AMOUNT("7286", "divAmount", (position, divAmount) -> position.setDivAmount(safeParseDouble(divAmount))),
    PE("7290", "P/E", (position, pe) -> position.setPe(safeParseDouble(pe))),
    EPS("7291", "EPS", (position, eps) -> position.setEps(safeParseDouble(eps))),
    HIGH_52("7293", " 52 Week High", (position, pe) -> position.setPe(safeParseDouble(pe))),
    LOW_52("7294", " 52 Week Low", (position, eps) -> position.setEps(safeParseDouble(eps))),
    TODAY_CLOSE_PRICE("7296", "todayClosePrice", (position, s) -> position.setHigh52(safeParseDouble(s))),
    PRIOR_CLOSE_PRICE("7741", "priorClosePrice", (position, s) -> position.setLow52(safeParseDouble(s)));

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

    private static double safeParseDouble(String value) {
        try {
            // Try parsing with US format (comma as grouping, period as decimal)
            NumberFormat format = NumberFormat.getInstance(Locale.US);
            Number number = format.parse(value);
            return number.doubleValue();
        } catch (Exception ignore) {
            try {
                // Try parsing with European format (period as grouping, comma as decimal)
                NumberFormat format = NumberFormat.getInstance(Locale.GERMANY);
                Number number = format.parse(value);
                return number.doubleValue();
            } catch (Exception ignore2) {
                try {
                    // Fallback to plain Double parsing (handles no separators)
                    return Double.parseDouble(value.replace(",", ""));
                } catch (Exception ignore3) {
                    return 0D; // Return 0 if all parsing attempts fail
                }
            }
        }
    }
}