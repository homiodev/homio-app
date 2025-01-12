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

  private IbkrService service;

  public Scratch3IBKRBlocks(Context context) {
    super("#b55050", context, null, "ibkr");
    setParent(ScratchParent.storage);

    // blocks
    blockReporter(10, "total_cash", "Total cash", workspaceBlock ->
      new DecimalType(getService(context).getTotalCash()));
    blockReporter(15, "equity_with_loan_value", "Equity with loan", workspaceBlock ->
      new DecimalType(getService(context).getEquityWithLoanValue()));
    blockReporter(25, "positions", "Positions", workspaceBlock ->
      new JsonType(getService(context).getPositions()));
    blockReporter(30, "orders", "Orders", workspaceBlock ->
      new JsonType(getService(context).getOrders()));
    blockReporter(35, "buyOrders", "Buy orders", workspaceBlock ->
      new JsonType(getService(context).getBuyOrders()));
    blockReporter(40, "sellOrders", "Sell orders", workspaceBlock ->
      new JsonType(getService(context).getSellOrders()));
    blockReporter(45, "dayPnl", "Day P&L", workspaceBlock ->
      new JsonType(getService(context).getDayPNL()));
  }

  private IbkrService getService(Context context) {
    if (service == null) {
      service = context.db().getRequire(IbkrEntity.class, PRIMARY_DEVICE).getOrCreateService(context).get();
    }
    return service;
  }
}
