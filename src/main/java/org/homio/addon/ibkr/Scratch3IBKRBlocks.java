package org.homio.addon.ibkr;

import lombok.Getter;
import org.homio.api.Context;
import org.homio.api.state.DecimalType;
import org.homio.api.state.JsonType;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.stereotype.Component;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@Getter
@Component
public class Scratch3IBKRBlocks extends Scratch3ExtensionBlocks {

    private final IbkrService service;

    public Scratch3IBKRBlocks(Context context) {
        super("#b55050", context, null, "ibkr");
        setParent(ScratchParent.storage);

        this.service = context.db().getEntityRequire(IbkrEntity.class, PRIMARY_DEVICE).getOrCreateService(context).get();

        // blocks
        blockReporter(5, "performance", "Performance", workspaceBlock ->
                new JsonType(service.getPerformance()));
        blockReporter(10, "total_cash", "Total cash", workspaceBlock ->
                new DecimalType(service.getTotalCash()));
        blockReporter(15, "available_funds", "Available funds", workspaceBlock ->
                new DecimalType(service.getAvailableFunds()));
        blockReporter(25, "positions", "Positions", workspaceBlock ->
                new JsonType(service.getPositions()));
        blockReporter(30, "orders", "Orders", workspaceBlock ->
                new JsonType(service.getOrders()));
        blockReporter(35, "buyOrders", "Buy orders", workspaceBlock ->
                new JsonType(service.getBuyOrders()));
        blockReporter(40, "sellOrders", "Sell orders", workspaceBlock ->
                new JsonType(service.getSellOrders()));
    }
}
