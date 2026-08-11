package com.tim.language_project.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link TranslationSegment}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TranslationSegmentId implements Serializable {

    private Long queryId;

    private Integer seqNo;
}
