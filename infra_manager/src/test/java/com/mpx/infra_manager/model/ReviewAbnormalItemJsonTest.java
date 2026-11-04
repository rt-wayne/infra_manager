package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: 新增：BACKLOG 第 158 項 R9——/home/review-abnormal 一筆序列化後不含 applyBy、標記日期只到日（比照 ExpiringAcctJsonTest）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class ReviewAbnormalItemJsonTest {

	@Test
	void serialize_excludesApplyBy_dateOnly() {
		ReviewAbnormalItem r = new ReviewAbnormalItem();
		r.setAcctId("AR20261012-004");
		r.setAcctName("sa");
		r.setCatgId("DB");
		r.setCatgName("資料庫帳號");
		r.setReviewFlagDate(LocalDateTime.of(2026, 10, 13, 14, 0));
		r.setReviewFlagReason("退回原因");
		r.setReviewFlagSourceCode("EMG");
		r.setApplyBy("U001");

		String json = JsonMapper.builder().build().writeValueAsString(r);

		assertThat(json).doesNotContain("applyBy").doesNotContain("U001");
		assertThat(json).contains("\"acctId\":\"AR20261012-004\"").contains("\"reviewFlagDate\":\"2026-10-13\"")
				.contains("\"reviewFlagSourceCode\":\"EMG\"");
	}
}
