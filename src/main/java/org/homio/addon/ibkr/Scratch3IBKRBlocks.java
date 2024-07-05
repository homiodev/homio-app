package org.homio.addon.ibkr;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.homio.api.Context;
import org.homio.api.state.DecimalType;
import org.homio.api.state.JsonType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Getter
@Component
public class Scratch3IBKRBlocks extends Scratch3ExtensionBlocks {

    private final MenuBlock.StaticMenuBlock<String> accountMenu;
    private final IbkrEntity entity;
    private final LoadingCache<String, ObjectNode> queryCache;

    public Scratch3IBKRBlocks(Context context) {
        super("#b55050", context, null, "ibkr");
        setParent(ScratchParent.storage);
        // account menu filled by IbkrService after success init
        accountMenu = menuStaticList("account", Map.of("-", "-"), "-");

        // blocks
        blockReporter(5, "performance", "Performance | [VALUE]", this::getPerformance,
                scratch3Block -> scratch3Block.addArgument(VALUE, accountMenu));
        blockReporter(10, "total_cash", "Total cash | [VALUE]", workspaceBlock ->
                        getSummary(workspaceBlock, "totalcashvalue"),
                scratch3Block -> scratch3Block.addArgument(VALUE, accountMenu));
        blockReporter(15, "available_funds", "Available funds | [VALUE]", workspaceBlock ->
                        getSummary(workspaceBlock, "availablefunds"),
                scratch3Block -> scratch3Block.addArgument(VALUE, accountMenu));

        blockReporter(20, "trades", "Trades | [VALUE]", workspaceBlock ->
                        getJson("iserver/account/trades"));

        this.entity = IbkrEntity.ensureEntity(context);

        this.queryCache = CacheBuilder.newBuilder().
                expireAfterWrite(1, TimeUnit.MINUTES).build(new CacheLoader<>() {
                    public @NotNull ObjectNode load(@NotNull String url) {
                        return Curl.get(entity.getUrl(url), ObjectNode.class);
                    }
                });
    }

    private State getJson(String url) {
        return new JsonType(Curl.get(entity.getUrl(url), ObjectNode.class));
    }

    private StringType getPerformance(WorkspaceBlock workspaceBlock) {
        String accountId = workspaceBlock.getMenuValue(VALUE, accountMenu);
        ObjectNode response = Curl.post(entity.getUrl("pa/performance"),
                new PerformanceRequest(Set.of(accountId)), ObjectNode.class);
        return new StringType(response.get("data").asText());
    }

    @SneakyThrows
    private @NotNull DecimalType getSummary(WorkspaceBlock workspaceBlock, String field) {
        String accountId = workspaceBlock.getMenuValue(VALUE, accountMenu);
        ObjectNode response = queryCache.get(entity.getUrl("portfolio/%s/summary".formatted(accountId)));
        return new DecimalType(response.get(field).get("amount").asText());
    }

    @Getter
    @AllArgsConstructor
    private static class PerformanceRequest {
        private final String freq = "D";
        private final Set<String> acctIds;
    }
}
