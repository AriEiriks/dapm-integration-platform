package com.dapm.security_service.models.dtos;

public class FilesDto {
    private String name;
    private long size;
    private String connectPath;

    public FilesDto() {}

    public FilesDto(String name, long size, String connectPath) {
        this.name = name;
        this.size = size;
        this.connectPath = connectPath;
    }

    public String getName() { return name; }
    public long getSize() { return size; }
    public String getConnectPath() { return connectPath; }

    public void setName(String name) { this.name = name; }
    public void setSize(long size) { this.size = size; }
    public void setConnectPath(String connectPath) { this.connectPath = connectPath; }
}
