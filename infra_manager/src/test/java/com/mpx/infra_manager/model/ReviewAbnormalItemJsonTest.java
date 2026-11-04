package com.mpx.infra_manager.model;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-11-04
// 變更說明: 新增：BACKLOG 第 158 項 R9——/home/review-abnormal 一筆序列化：標記日期只到日、七個欄位都在、沒有多餘欄位
//           （R9 審查 S3 拿掉 applyBy 後改驗欄位集合，防止日後加回工號類欄位沒人注意）
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class ReviewAbnormalItemJsonTest {

	@Test
	void serialize_dateOnly_exactFieldSet() {
		ReviewAbnormalItem r = new ReviewAbnormalItem();
		r.setAcctId("AR20261012-004");
		r.setAcctName("sa");
		r.setCatgId("DB");
		r.setCatgName("資料庫帳號");
		r.setReviewFlagDate(LocalDateTime.of(2026, 10, 13, 14, 0));
		r.setReviewFlagReason("退回原因");
		r.setReviewFlagSourceCode("EMG");

		String json = JsonMapper.builder().build().writeValueAsString(r);

		assertThat(json).isEqualTo("{\"acctId\":\"AR20261012-004\",\"acctName\":\"sa\",\"catgId\":\"DB\",\"catgName\":\"資料庫帳號\","
				+ "\"reviewFlagDate\":\"2026-10-13\",\"reviewFlagReason\":\"退回原因\",\"reviewFlagSourceCode\":\"EMG\"}");
	}
}
