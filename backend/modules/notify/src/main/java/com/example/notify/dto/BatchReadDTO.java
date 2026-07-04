package com.example.notify.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class BatchReadDTO {

  @NotEmpty private List<Long> ids;
}
