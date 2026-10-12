package com.railmind.station.dto;

public record StationDto(Long id, String code, String name, String city, String state, boolean active) {
    public static StationDto from(com.railmind.station.Station s) {
        return new StationDto(s.getId(), s.getCode(), s.getName(), s.getCity(), s.getState(), s.isActive());
    }
}
