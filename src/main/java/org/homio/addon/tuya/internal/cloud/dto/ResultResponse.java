package org.homio.addon.tuya.internal.cloud.dto;


import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

@ToString
public class ResultResponse<T> {

    public boolean success = false;
    public long code = 0;
    @SerializedName("t")
    public long timestamp = 0;

    public @Nullable String msg;
    public @Nullable T result;
}
