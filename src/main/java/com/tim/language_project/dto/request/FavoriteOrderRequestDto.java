package com.tim.language_project.dto.request;

import java.util.List;

/**
 * 收藏清單拖曳排序後的新順序：{ "queryIds": [88, 137, 42] }。
 *
 * 送的是排好的完整清單，不是「把某一筆移到第幾位」——
 * 前端本來就握有整個陣列，這樣做是冪等的，重送一次結果一樣。
 *
 * ★ 型別一定要是 List 不能是 Set。Set 不保證順序，
 *   而這支請求的全部意義就是順序。
 */
public record FavoriteOrderRequestDto(List<Long> queryIds) {
}
