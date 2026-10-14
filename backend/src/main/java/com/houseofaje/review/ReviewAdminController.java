package com.houseofaje.review;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reviews")
public class ReviewAdminController {

    private final ReviewService reviews;

    public ReviewAdminController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public List<Review> list(@RequestParam(required = false) ReviewStatus status,
                             @RequestParam(defaultValue = "50") int limit) {
        return reviews.list(status, limit);
    }

    @PostMapping("/{id}/hide")
    public Review hide(@PathVariable UUID id) {
        return reviews.setStatus(id, ReviewStatus.HIDDEN);
    }

    @PostMapping("/{id}/publish")
    public Review publish(@PathVariable UUID id) {
        return reviews.setStatus(id, ReviewStatus.PUBLISHED);
    }
}
