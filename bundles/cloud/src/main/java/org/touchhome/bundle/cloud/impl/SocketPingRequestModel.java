package org.touchhome.bundle.cloud.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
class SocketPingRequestModel extends SocketBaseModel {
    private String user;
}
