package com.FBLA.WebCodingDev26Backend.dto;

import com.FBLA.WebCodingDev26Backend.model.FoundItem;
import java.util.List;

public record PublicFoundItemResponse(
        String id,
        String title,
        String description,
        String category,
        String subcategory,
        String color,
        String brand,
        String locationFound,
        String dateFound,
        String timeFound,
        String status,
        String recordType,
        String createdDate,
        String updatedDate,
        String condition,
        String priority,
        String itemCode,
        String eventHubId,
        String campusZoneId,
        List<String> photoUrls,
        List<String> tags
) {
    public static PublicFoundItemResponse from(FoundItem item) {
        return new PublicFoundItemResponse(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getCategory(),
                item.getSubcategory(),
                item.getColor(),
                item.getBrand(),
                item.getLocationFound(),
                item.getDateFound(),
                item.getTimeFound(),
                item.getStatus(),
                item.getRecordType(),
                item.getCreatedDate(),
                item.getUpdatedDate(),
                item.getCondition(),
                item.getPriority(),
                item.getItemCode(),
                item.getEventHubId(),
                item.getCampusZoneId(),
                item.getPhotoUrls(),
                item.getTags()
        );
    }
}
