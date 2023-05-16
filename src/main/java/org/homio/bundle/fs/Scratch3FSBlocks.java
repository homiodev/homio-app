package org.homio.bundle.fs;

import static org.homio.bundle.api.util.CommonUtils.getErrorMessage;

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
import org.apache.commons.lang3.StringUtils;
import org.homio.app.cb.ComputerBoardEntity;
import org.homio.bundle.api.BundleEntrypoint;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.EntityFieldMetadata;
import org.homio.bundle.api.fs.FileSystemProvider;
import org.homio.bundle.api.fs.FileSystemProvider.UploadOption;
import org.homio.bundle.api.fs.TreeNode;
import org.homio.bundle.api.state.DecimalType;
import org.homio.bundle.api.state.RawType;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.workspace.WorkspaceBlock;
import org.homio.bundle.api.workspace.scratch.ArgumentType;
import org.homio.bundle.api.workspace.scratch.MenuBlock;
import org.homio.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.bundle.fs.Scratch3FSBlocks.ModifyFileSettings.ModifyOption;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

@Getter
@Component
public class Scratch3FSBlocks extends Scratch3ExtensionBlocks {

    private final MenuBlock.ServerMenuBlock fsEntityMenu;
    private final MenuBlock.ServerMenuBlock fileMenu;
    private final MenuBlock.ServerMenuBlock folderMenu;
    private final MenuBlock.StaticMenuBlock<Unit> unitMenu;
    private final MenuBlock.StaticMenuBlock<CountNodeEnum> countMenu;

    public Scratch3FSBlocks(EntityContext entityContext) {
        super("#93922C", entityContext, null, "fs");
        setParent("storage");

        // menu
        this.fsEntityMenu = menuServerItems(ENTITY, ComputerBoardEntity.class, "FileSystem");
        this.unitMenu = menuStatic("UNIT", Unit.class, Unit.B);
        this.countMenu = menuStatic("COUNT", CountNodeEnum.class, CountNodeEnum.All);
        this.fileMenu = menuServerFiles(this.fsEntityMenu, null);
        this.folderMenu = menuServerFolders(this.fsEntityMenu, null);

        // blocks
        blockCommand(15, "modify_file",
            "Update [VALUE] as [NAME] to [PARENT] | [SETTING]", this::sendFileHandle,
            block -> {
                block.addArgument(VALUE, ArgumentType.string, "body");
                block.addArgument("NAME", "test.txt");
                block.addArgument("PARENT", this.folderMenu);
                block.addArgument("CONTENT", ArgumentType.string);
                block.addSetting(ModifyFileSettings.class);
            });

        blockReporter(20, "get_file_content", "Get [FILE] of [ENTITY]", this::getFieldContent,
            block -> {
                block.addArgument(ENTITY, this.fsEntityMenu);
                block.addArgument("FILE", this.fileMenu);
            });

        blockReporter(30, "get_count", "Count of [VALUE] in [PARENT] [ENTITY]", this::getCountOfNodesReporter,
            block -> {
                block.addArgument(ENTITY, this.fsEntityMenu);
                block.addArgument("PARENT", this.folderMenu);
                block.addArgument(VALUE, this.countMenu);
            });

        blockReporter(35, "get_used_quota", "Used quota of [ENTITY] | in [UNIT]", this::getUsedQuotaReporter,
            block -> {
                block.addArgument(ENTITY, this.fsEntityMenu);
                block.addArgument("UNIT", this.unitMenu);
            });

        blockReporter(40, "get_total_quota", "Total quota of [ENTITY] | in [UNIT]", this::getTotalQuotaReporter,
            block -> {
                block.addArgument(ENTITY, this.fsEntityMenu);
                block.addArgument("UNIT", this.unitMenu);
            });

        blockCommand(50, "delete", "Delete [FILE] of [ENTITY]", this::deleteFileHandle,
            block -> {
                block.addArgument(ENTITY, this.fsEntityMenu);
                block.addArgument("FILE", this.fileMenu);
            });
    }

    public static byte[] addAll(final byte[] array1, byte[] array2) {
        byte[] joinedArray = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }

    private DecimalType getCountOfNodesReporter(WorkspaceBlock workspaceBlock) {
        CountNodeEnum countNodeEnum = workspaceBlock.getMenuValue(VALUE, this.countMenu);
        FileSystemProvider fileSystem = getDrive(workspaceBlock).getFileSystem(entityContext);
        String folderId = workspaceBlock.getMenuValue("PARENT", this.folderMenu);
        Set<TreeNode> children = fileSystem.getChildren(folderId);
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
                for (TreeNode treeNode : fileSystem.getChildrenRecursively(folderId)) {
                    treeNode.visitTree(filesFilter);
                }
                return new DecimalType(filesCounter.get());
            case AllWithChildren:
                AtomicInteger allNodesCounter = new AtomicInteger(0);
                Consumer<TreeNode> allNodesFilter = treeNode -> allNodesCounter.incrementAndGet();
                for (TreeNode treeNode : fileSystem.getChildrenRecursively(folderId)) {
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
        return new DecimalType(getDrive(workspaceBlock).getFileSystem(entityContext).getTotalSpace() / unit);
    }

    private DecimalType getUsedQuotaReporter(WorkspaceBlock workspaceBlock) {
        double unit = workspaceBlock.getMenuValue("UNIT", this.unitMenu).divider;
        return new DecimalType(getDrive(workspaceBlock).getFileSystem(entityContext).getUsedSpace() / unit);
    }

    private void deleteFileHandle(WorkspaceBlock workspaceBlock) {
        String fileId = workspaceBlock.getMenuValue("FILE", this.fileMenu);
        if (!"-".equals(fileId)) {
            try {
                getDrive(workspaceBlock).getFileSystem(entityContext).delete(Collections.singleton(fileId));
            } catch (Exception ex) {
                workspaceBlock.logErrorAndThrow("Unable to delete file: <{}>. Msg: ",
                    fileId, getErrorMessage(ex));
            }
        } else {
            workspaceBlock.logErrorAndThrow("Delete file block requires file name");
        }
    }

    private ComputerBoardEntity getDrive(WorkspaceBlock workspaceBlock) {
        return workspaceBlock.getMenuValueEntityRequired(ENTITY, this.fsEntityMenu);
    }

    private RawType getFieldContent(WorkspaceBlock workspaceBlock) throws Exception {
        String fileId = workspaceBlock.getMenuValue("FILE", this.fileMenu);
        if (!"-".equals(fileId)) {
            String[] path = fileId.split("~~~");
            String id = path[path.length - 1];
            FileSystemProvider fileSystem = getDrive(workspaceBlock).getFileSystem(entityContext);
            TreeNode treeNode = fileSystem.toTreeNode(id);
            byte[] content = IOUtils.toByteArray(treeNode.getInputStream());

            return new RawType(content,
                StringUtils.defaultIfEmpty(treeNode.getAttributes().getContentType(), MimeTypeUtils.TEXT_PLAIN_VALUE),
                treeNode.getName());
        }
        return null;
    }

    @SneakyThrows
    private void sendFileHandle(WorkspaceBlock workspaceBlock) {
        String fileName = workspaceBlock.getInputStringRequired("NAME", "Send file block requires file name");
        byte[] value = workspaceBlock.getInputByteArray(VALUE);

        String folderId = workspaceBlock.getMenuValue("PARENT", this.folderMenu);
        try {
            String fsEntityID = folderId.substring(0, folderId.indexOf(":"));
            FileSystemProvider fileSystem = entityContext.getEntity(fsEntityID);
            // FileSystemProvider fileSystem = getDrive(workspaceBlock).getFileSystem(entityContext);
            ModifyFileSettings setting = workspaceBlock.getSetting(ModifyFileSettings.class);

            //  List<UploadOption> uploadOptions =
            //        workspaceBlock.getMenuValues("OPTIONS", this.uploadOptionsMenu, UploadOption.class, "~~~");

            FileSystemProvider.UploadOption uploadOption =
                setting.getModifyOption() == ModifyOption.Append ? UploadOption.Append : FileSystemProvider.UploadOption.Replace;
            if (setting.prependNewLine) {
                value = addAll("\n".getBytes(), value);
            }
            if (setting.appendNewLine) {
                value = addAll(value, "\n".getBytes());
            }

            fileSystem.copy(TreeNode.of(fileName, value), folderId, uploadOption);
        } catch (Exception ex) {
            workspaceBlock.logError("Unable to store file: <{}>. Msg: <{}>", fileName, ex.getMessage());
        }
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

        @UIField(order = 1, icon = "fa fa-pen")
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

    public static class FakeEntrypoint implements BundleEntrypoint {

        @Override
        public void init() {

        }

        @Override
        public String getBundleId() {
            return "local";
        }

        @Override
        public int order() {
            return 0;
        }
    }
}
