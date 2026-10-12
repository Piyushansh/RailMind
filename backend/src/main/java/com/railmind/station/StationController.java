package com.railmind.station;

import com.railmind.station.dto.StationDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    public List<StationDto> search(@RequestParam(required = false) String query) {
        return stationService.search(query);
    }
}
