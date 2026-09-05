package com.spark.falcon.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserGroupRequest {

    @NotBlank(message = "Enter a group name.")
    @Size(min = 2, max = 120, message = "Group name must contain 2 to 120 characters.")
    private String name;

    @Size(max = 140, message = "Slug is too long.")
    @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug may contain lowercase letters, numbers and hyphens only.")
    private String slug;
}
