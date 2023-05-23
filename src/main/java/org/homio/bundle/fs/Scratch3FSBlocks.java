package org.homio.bundle.fs;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.homio.app.utils.InternalUtil.TIKA;
import static org.homio.bundle.api.util.CommonUtils.getErrorMessage;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.homio.app.model.entity.LocalBoardEntity;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.EntityFieldMetadata;
import org.homio.bundle.api.entity.storage.BaseFileSystemEntity;
import org.homio.bundle.api.fs.FileSystemProvider;
import org.homio.bundle.api.fs.FileSystemProvider.UploadOption;
import org.homio.bundle.api.fs.TreeNode;
import org.homio.bundle.api.state.DecimalType;
import org.homio.bundle.api.state.RawType;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldType;
import org.homio.bundle.api.workspace.WorkspaceBlock;
import org.homio.bundle.api.workspace.scratch.ArgumentType;
import org.homio.bundle.api.workspace.scratch.MenuBlock;
import org.homio.bundle.api.workspace.scratch.MenuBlock.ServerMenuBlock;
import org.homio.bundle.api.workspace.scratch.MenuBlock.StaticMenuBlock;
import org.homio.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.bundle.fs.Scratch3FSBlocks.ModifyFileSettings.ModifyOption;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3FSBlocks extends Scratch3ExtensionBlocks {

    private final MenuBlock.ServerMenuBlock fileMenu;
    private final MenuBlock.ServerMenuBlock folderMenu;
    private final MenuBlock.StaticMenuBlock<Unit> unitMenu;
    private final StaticMenuBlock<NodeType> nodeTypeMenu;
    private final MenuBlock.StaticMenuBlock<CountNodeEnum> countMenu;
    private final ServerMenuBlock fsEntityMenu;

    public Scratch3FSBlocks(EntityContext entityContext) {
        super("#93922C", entityContext, null, "fs");
        setParent("storage");

        // menu
        this.fsEntityMenu = menuServerItems(ENTITY, LocalBoardEntity.class, "FileSystem");
        this.unitMenu = menuStatic("UNIT", Unit.class, Unit.B);
        this.countMenu = menuStatic("COUNT", CountNodeEnum.class, CountNodeEnum.All);
        this.fileMenu = menuServerFiles(null);
        this.folderMenu = menuServerFolders(null);
        this.nodeTypeMenu = menuStatic("NT", NodeType.class, NodeType.File);

        // blocks
        blockCommand(15, "update_file",
            "Update [VALUE] of [FILE] | [SETTING]", this::updateFileHandle,
            block -> {
                block.addArgument(VALUE, ArgumentType.string, "body");
                block.addArgument("FILE", fileMenu);
                block.addArgument("VALUE", "Content");
                block.addSetting(ModifyFileSettings.class);
            });

        blockCommand(16, "create_file",
            "Create file [NAME] of [FOLDER] | [SETTING]", this::createHandle,
            block -> {
                block.addArgument(VALUE, ArgumentType.string, "body");
                block.addArgument("TYPE", nodeTypeMenu);
                block.addArgument("NAME", "Test.txt");
                block.addArgument("FOLDER", folderMenu);
            });

        blockReporter(20, "get_file_content", "Get [FILE]", this::getFieldContent,
            block -> block.addArgument("FILE", fileMenu));

        blockReporter(30, "get_count", "Count of [VALUE] in [FOLDER]", this::getCountOfNodesReporter,
            block -> {
                block.addArgument("FOLDER", folderMenu);
                block.addArgument(VALUE, this.countMenu);
            });

        blockReporter(35, "get_used_quota", "Used quota of [ENTITY] | in [UNIT]", this::getUsedQuotaReporter,
            block -> {
                block.addArgument(ENTITY, fsEntityMenu);
                block.addArgument("UNIT", unitMenu);
            });

        blockReporter(40, "get_total_quota", "Total quota of [ENTITY] | in [UNIT]", this::getTotalQuotaReporter,
            block -> {
                block.addArgument(ENTITY, fsEntityMenu);
                block.addArgument("UNIT", unitMenu);
            });

        blockCommand(50, "delete", "Delete [FILE]", this::deleteFileHandle,
            block -> block.addArgument("FILE", fileMenu));
    }

    public static byte[] addAll(final byte[] array1, byte[] array2) {
        byte[] joinedArray = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }

    private DecimalType getCountOfNodesReporter(WorkspaceBlock workspaceBlock) {
        CountNodeEnum countNodeEnum = workspaceBlock.getMenuValue(VALUE, this.countMenu);
        FileSystemItem fsItem = getFolderId(workspaceBlock);
        Set<TreeNode> children = fsItem.fileSystem.getChildren(fsItem.node);
        switch (countNodeEnum) {
            case Files:
                return new DecimalType(children.stream().filter(c -> !c.getAttributes().isDir()).count());
            case Folders:
                return new DecimalType(children.stream().filter(c -> c.getAttributes().isDir()).count());
            case All:
                return new DecimalType(children.size());
            case FilesWithChildren:
                AtomicInteger filesCounter = new AtomicInteger(0);
                Consumer<TreeNode> filesFilter = treeNode -> {
                    if (!treeNode.getAttributes().isDir()) {
                        filesCounter.incrementAndGet();
                    }
                };
                for (TreeNode treeNode : fsItem.fileSystem.getChildrenRecursively(fsItem.node)) {
                    treeNode.visitTree(filesFilter);
                }
                return new DecimalType(filesCounter.get());
            case AllWithChildren:
                AtomicInteger allNodesCounter = new AtomicInteger(0);
                Consumer<TreeNode> allNodesFilter = treeNode -> allNodesCounter.incrementAndGet();
                for (TreeNode treeNode : fsItem.fileSystem.getChildrenRecursively(fsItem.node)) {
                    treeNode.visitTree(allNodesFilter);
                }
                return new DecimalType(allNodesCounter.get());
        }
        throw new RuntimeException("Unable to handle unknown count enum type: " + countNodeEnum);
    }

    /*public void init() {
        this.fsEntityMenu.setDefault(entityContext.findAny(entityClass));
        super.init();
    }*/

    @SneakyThrows
    private DecimalType getTotalQuotaReporter(WorkspaceBlock workspaceBlock) {
        double unit = workspaceBlock.getMenuValue("UNIT", this.unitMenu).divider;
        return new DecimalType(getFileSystem(workspaceBlock).getTotalSpace() / unit);
    }

    private DecimalType getUsedQuotaReporter(WorkspaceBlock workspaceBlock) {
        double unit = workspaceBlock.getMenuValue("UNIT", this.unitMenu).divider;
        return new DecimalType(getFileSystem(workspaceBlock).getUsedSpace() / unit);
    }

    private void deleteFileHandle(WorkspaceBlock workspaceBlock) {
        FileSystemItem fsItem = getFileId(workspaceBlock);
        try {
            fsItem.fileSystem.delete(Collections.singleton(fsItem.node));
        } catch (Exception ex) {
            workspaceBlock.logErrorAndThrow("Unable to delete file: <{}>. Msg: ",
                fsItem.node, getErrorMessage(ex));
        }
    }

    private FileSystemProvider getFileSystem(WorkspaceBlock workspaceBlock) {
        BaseFileSystemEntity entity = workspaceBlock.getMenuValueEntityRequired(ENTITY, this.fsEntityMenu);
        return entity.getFileSystem(entityContext);
    }

    private RawType getFieldContent(WorkspaceBlock workspaceBlock) throws Exception {
        FileSystemItem fsItem = getFileId(workspaceBlock);
        TreeNode treeNode = fsItem.fileSystem.toTreeNode(fsItem.node);
        try (InputStream stream = treeNode.getInputStream()) {
            byte[] content = IOUtils.toByteArray(stream);
            return new RawType(content, getContentType(treeNode, content), treeNode.getName());
        }
    }

    private String getContentType(TreeNode treeNode, byte[] content) {
        String contentType = treeNode.getAttributes().getContentType();
        if (isEmpty(contentType)) {
            contentType = TIKA.detect(treeNode.getName());
            if (isEmpty(contentType) || TEXT_PLAIN_VALUE.equals(contentType)) {
                contentType = TIKA.detect(content);
            }
        }
        return defaultIfEmpty(contentType, TEXT_PLAIN_VALUE);
    }

    @SneakyThrows
    private void createHandle(WorkspaceBlock workspaceBlock) {
        NodeType nodeType = workspaceBlock.getMenuValue("TYPE", nodeTypeMenu);
        String fileName = workspaceBlock.getInputStringRequired("NAME", "File name is missing or empty");

        FileSystemItem fsItem = getFolderId(workspaceBlock);
        fsItem.fileSystem.create(fsItem.node, fileName, nodeType == NodeType.Folder, UploadOption.SkipExist);
    }

    @SneakyThrows
    private void updateFileHandle(WorkspaceBlock workspaceBlock) {
        byte[] value = workspaceBlock.getInputByteArray(VALUE);

        FileSystemItem fsItem = getFileId(workspaceBlock);
        try {
            ModifyFileSettings setting = workspaceBlock.getSetting(ModifyFileSettings.class);

            FileSystemProvider.UploadOption uploadOption =
                setting.getModifyOption() == ModifyOption.Append ? UploadOption.Append : FileSystemProvider.UploadOption.Replace;
            if (setting.prependNewLine) {
                value = addAll("\n".getBytes(), value);
            }
            if (setting.appendNewLine) {
                value = addAll(value, "\n".getBytes());
            }

            fsItem.fileSystem.copy(TreeNode.of(fsItem.node, value), fsItem.node, uploadOption);
        } catch (Exception ex) {
            workspaceBlock.logError("Unable to store file: <{}>. Msg: <{}>", fsItem.node, ex.getMessage());
        }
    }

    private FileSystemItem getFileId(WorkspaceBlock workspaceBlock) {
        return getItemId("FILE", workspaceBlock);
    }

    private FileSystemItem getFolderId(WorkspaceBlock workspaceBlock) {
        return getItemId("FOLDER", workspaceBlock);
    }

    private FileSystemItem getItemId(String key, WorkspaceBlock workspaceBlock) {
        String[] ids = workspaceBlock.getMenuValue(key, this.fileMenu).split("~~~");
        BaseFileSystemEntity entity = getEntityContext().getEntityRequire(ids[0]);
        String node = ids[1];
        int splitNameAndId = node.indexOf("://");
        if (splitNameAndId >= 0) {
            node = node.substring(splitNameAndId + "://".length());
        }
        return new FileSystemItem(entity.getFileSystem(entityContext), entity, node);
    }

    @RequiredArgsConstructor
    private static class FileSystemItem {

        private final FileSystemProvider fileSystem;
        private final BaseFileSystemEntity entity;
        private final String node;
    }

    @RequiredArgsConstructor
    private enum Unit {
        B(1), KB(1024), MP(1024 * 1024), GB(1024 * 1024 * 1024);
        private final double divider;
    }

    private enum CountNodeEnum {
        Files, Folders, All, FilesWithChildren, AllWithChildren
    }

    @Getter
    @Setter
    public static class ModifyFileSettings implements EntityFieldMetadata {

        @UIField(order = 1, icon = "fa fa-pen", type = UIFieldType.EnumButtons)
        private ModifyOption modifyOption = ModifyOption.Overwrite;

        @UIField(order = 2)
        private boolean prependNewLine = false;

        @UIField(order = 2)
        private boolean appendNewLine = false;

        @Override
        public @NotNull String getEntityID() {
            return "yes_no";
        }

        public enum ModifyOption {
            Overwrite, Append
        }
    }

    public enum NodeType {
        File, Folder
    }
}
