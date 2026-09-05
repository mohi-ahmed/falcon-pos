package com.spark.falcon.user.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class GroupPermissionRequest {
    private Set<@Positive Long> permissionIds = new LinkedHashSet<>();
}
