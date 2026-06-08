package com.webank.wedpr.components.publish.sync;
/**
 * @author zachma
 * @date 2024/8/31
 */
public enum PublishSyncAction {
    // 同步
    SYNC("SyncPublish"),
    // 收回
    REVOKE("RevokePublish");

    private final String action;

    PublishSyncAction(String action) {
        this.action = action;
    }

    public String getAction() {
        return this.action;
    }
}
