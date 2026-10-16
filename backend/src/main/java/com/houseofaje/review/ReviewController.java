package com.houseofaje.review;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/reviews")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Review create(@RequestBody CreateReviewRequest request) {
        return reviews.create(request);
    }

    @GetMapping
    public List<PublishedReview> list(@RequestParam UUID serviceId,
                                      @RequestParam(defaultValue = "10") int limit) {
        return reviews.publishedByService(serviceId, limit);
    }

    @GetMapping("/latest")
    public ReviewSummary latest(@RequestParam(defaultValue = "3") int limit) {
        return reviews.summary(limit);
    }
}
