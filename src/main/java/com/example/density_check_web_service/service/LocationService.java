package com.example.density_check_web_service.service;

import com.example.density_check_web_service.domain.Location.Location;
import com.example.density_check_web_service.domain.Location.LocationRepository;
import com.example.density_check_web_service.domain.Location.dto.LocationRequestDto;
import com.example.density_check_web_service.domain.Location.dto.LocationResponseDto;
import com.example.density_check_web_service.domain.Location.dto.LocationResponseForUserDto;
import com.example.density_check_web_service.domain.PiAddress.PiAddress;
import com.example.density_check_web_service.domain.PiAddress.PiAddressRepository;
import com.example.density_check_web_service.domain.PiAddress.dto.PiAddressResponseDto;
import com.example.density_check_web_service.domain.Users.Users;
import com.example.density_check_web_service.domain.Users.UsersRepository;
import com.example.density_check_web_service.domain.Users.dto.UsersResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class LocationService {
    private final LocationRepository locationRepository;
    private final PiAddressRepository piAddressRepository;
    private final UsersRepository usersRepository;

    @Transactional
    public void saveLocation(LocationRequestDto locationRequestDto) {
        PiAddress tmp = piAddressRepository.findByAddress(locationRequestDto.getAddress())
                .orElse(piAddressRepository.save(new PiAddress(locationRequestDto.getAddress())));
        if (locationRepository.countByPiAddress(tmp) > 1000) {
            Location location = locationRepository.findFirstByPiAddressOrderByModifiedDateAsc(tmp);
            location.setX(locationRequestDto.getX());
            location.setY(locationRequestDto.getY());
            locationRepository.save(location);
        }
        else {
            locationRepository.save(locationRequestDto.toEntity(tmp));
        }
        //Test Data
        List<Location> locations = new ArrayList<>();
        int[] distribution = {5, 14, 14, 15, 25, 32, 32, 37, 44, 46, 46, 48, 59, 63, 63, 66, 72, 75, 77, 78, 90, 91, 95, 99};
        int index = 0;
        List<PiAddress> piAddresses = piAddressRepository.findAll();

        for(int i = 0; i < 100; i++) {
            while(distribution[index] < i) {
                index++;
            }
            Location location = Location.builder().piAddress(piAddresses.get(i)).distance(0).x(index/4).y(index%4).build();
            locations.add(location);
        }
        locationRepository.saveAll(locations);
    }

    @Transactional
    public List<LocationResponseDto> allLocationToJson(){
        List<Location> entity = locationRepository.findFirst10ByOrderByIdDesc();
        return entity.stream()
                .map(LocationResponseDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<LocationResponseDto> eachLocationToJson(){
        List<PiAddress> allAddress = piAddressRepository.findAll();
        List<Location> entity = new ArrayList<>();
        for(PiAddress tmp : allAddress) {
            Location location = locationRepository.findFirstByPiAddressOrderByModifiedDateDesc(tmp);
            if (location != null && location.getModifiedDate().isAfter(LocalDateTime.now().minusMinutes(1)))
                entity.add(location);
        }

        if(entity.isEmpty()) {
            return new ArrayList<>();
        }

        return entity.stream()
                .map(LocationResponseDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public LocationResponseForUserDto findLocationByEmail(String email, Boolean my) {
        PiAddress piAddress = piAddressRepository.findByEmail(email).orElse(null);
        if(piAddress == null) {
            Users users = usersRepository.findByEmail(email).orElse(null);
            piAddress = new PiAddress("111.111.111.111");
            piAddress.update(users);
            piAddress = piAddressRepository.saveAndFlush(piAddress);
            locationRepository.saveAndFlush(new Location(piAddress, 0, 0, 0));

//            return new LocationResponseForUserDto(new Location(null, 0, 0, 0), 0);
        }
        Location location = locationRepository.findFirstByPiAddressOrderByModifiedDateAsc(piAddress);
        List<Location> locations = new ArrayList<>();
        if (my)
            locations = locationRepository.findByXAndYAndModifiedDateIsGreaterThanEqualOrderByModifiedDateDesc(location.getX(), location.getY(), LocalDateTime.now().minusMinutes(1));
        else
            locations = locationRepository.findByXAndYAndModifiedDateIsGreaterThanEqualOrderByModifiedDateDesc(location.getX(), location.getY(), location.getModifiedDate().minusMinutes(1));

        Set<PiAddress> set = locations.stream().map(l -> {
            return l.getPiAddress();
        }).collect(Collectors.toSet());
        return new LocationResponseForUserDto(location, set.size());
    }

    @Transactional
    public List<UsersResponseDto> findUsersByEmail(String email) {
        PiAddress piAddress = piAddressRepository.findByEmail(email).orElse(null);
        if(piAddress == null) {
            return new ArrayList<>();
        }
        Location location = locationRepository.findFirstByPiAddressOrderByModifiedDateAsc(piAddress);
        List<Location> locations = locationRepository.findByXAndYAndModifiedDateIsGreaterThanEqualOrderByModifiedDateDesc(location.getX(), location.getY(), LocalDateTime.now().minusMinutes(1));
        Set<PiAddress> set = locations.stream().map(l -> {
            return l.getPiAddress();
        }).collect(Collectors.toSet());
        set.remove(piAddress);
        List<UsersResponseDto> usersResponseDtos = set.stream().map(l->UsersResponseDto.builder().entity(l.getUsers()).build()).collect(Collectors.toList());
        usersResponseDtos.sort(Comparator.comparing(UsersResponseDto::getName));
        return usersResponseDtos;
    }

    @Transactional
    public List<PiAddressResponseDto> findUserByArea(int x, int y, int duration) {
        List<Location> locations = locationRepository.findByXAndYAndModifiedDateIsGreaterThanEqualOrderByModifiedDateDesc(x, y, LocalDateTime.now().minusMinutes(duration));
        Set<PiAddress> set = locations.stream().map(location -> {
            return location.getPiAddress();
        }).collect(Collectors.toSet());
        List<PiAddressResponseDto> modifiedLocation = new ArrayList<>();

        for (Location location : locations) {
            PiAddress piAddress = location.getPiAddress();
            if (set.contains(piAddress)) {
                modifiedLocation.add(PiAddressResponseDto.builder().entity(location.getPiAddress()).build());
                set.remove(piAddress);
            }
        }

        return modifiedLocation;
    }
}
