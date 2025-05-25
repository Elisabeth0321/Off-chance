package com.lizaveta.model.fileDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileInfoDto {
    private String id;
    private String name;
    private FileType type;
}
