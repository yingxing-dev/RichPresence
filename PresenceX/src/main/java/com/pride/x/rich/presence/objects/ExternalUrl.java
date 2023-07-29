package com.pride.x.rich.presence.objects;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class ExternalUrl implements Serializable {
    @SerializedName("external_asset_path")
    public String external_asset_path;
}
