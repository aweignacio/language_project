package com.tim.language_project.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * {@link TranslationSegment} 的複合主鍵。
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
