package com.yef.fota.dto.device;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceImportResponse {

    private int totalRows;
    private int importedCount;
    private int duplicateInFileCount;
    private int duplicateInDatabaseCount;
    private int invalidImeiCount;
    private String summary;
}
