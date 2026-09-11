/**********************************************************************
* This file is part of iDempiere ERP Open Source                      *
* http://www.idempiere.org                                            *
*                                                                     *
* Copyright (C) Contributors                                          *
*                                                                     *
* This program is free software; you can redistribute it and/or       *
* modify it under the terms of the GNU General Public License         *
* as published by the Free Software Foundation; either version 2      *
* of the License, or (at your option) any later version.              *
*                                                                     *
* This program is distributed in the hope that it will be useful,     *
* but WITHOUT ANY WARRANTY; without even the implied warranty of      *
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the        *
* GNU General Public License for more details.                        *
*                                                                     *
* You should have received a copy of the GNU General Public License   *
* along with this program; if not, write to the Free Software         *
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,          *
* MA 02110-1301, USA.                                                 *
*                                                                     *
* Contributors:                                                       *
* - Trek Global Corporation                                           *
* - Murilo Torino                                                     *
**********************************************************************/
package com.trekglobal.idempiere.rest.api.webhook.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.compiere.model.MOrder;
import org.compiere.util.Env;
import org.junit.jupiter.api.Test;

import com.trekglobal.idempiere.rest.api.json.test.RestTestCase;
import com.trekglobal.idempiere.rest.api.model.MRestWebhookOutEvent;

/**
 * Tests for {@link MRestWebhookOutEvent#isConditionMet} — the per-subscription
 * outbound webhook condition (#527).
 * <p>The condition is evaluated with {@link org.compiere.util.DefaultEvaluatee}, the
 * same value resolution as display/read-only logic, so an in-memory (unsaved) C_Order
 * is used as the source record to exercise real column metadata (Yes/No, amount, ...).
 */
public class WebhookOutConditionTest extends RestTestCase {

	/**
	 * Subscription whose {@code getCondition()} returns {@code condition} and whose
	 * {@code isConditionMet(PO)} runs the real implementation.
	 */
	private static MRestWebhookOutEvent subscription(String condition) {
		MRestWebhookOutEvent sub = mock(MRestWebhookOutEvent.class);
		when(sub.getCondition()).thenReturn(condition);
		when(sub.isConditionMet(any())).thenCallRealMethod();
		return sub;
	}

	/** In-memory (unsaved) sales order carrying the given column values. */
	private static MOrder order(boolean soTrx, String docStatus, String description, String grandTotal) {
		MOrder o = new MOrder(Env.getCtx(), 0, null);
		o.setIsSOTrx(soTrx);
		if (docStatus != null)
			o.setDocStatus(docStatus);
		if (description != null)
			o.setDescription(description);
		if (grandTotal != null)
			o.setGrandTotal(new BigDecimal(grandTotal));
		return o;
	}

	@Test
	public void emptyConditionDeliversEverything() {
		assertTrue(subscription(null).isConditionMet(order(true, null, null, null)));
		assertTrue(subscription("").isConditionMet(order(true, null, null, null)));
		assertTrue(subscription("   ").isConditionMet(order(true, null, null, null)));
	}

	@Test
	public void nullRecordDelivers() {
		assertTrue(subscription("@IsSOTrx@=Y").isConditionMet(null));
	}

	@Test
	public void yesNoColumnMatchedAsYN() {
		// Yes/No columns must match with the Y/N convention (DefaultEvaluatee), not true/false.
		assertTrue(subscription("@IsSOTrx@=Y").isConditionMet(order(true, null, null, null)));
		assertFalse(subscription("@IsSOTrx@=Y").isConditionMet(order(false, null, null, null)));
		// Quoted values and AND, mirroring a real subscription condition.
		assertTrue(subscription("@IsSOTrx@='Y' & @Description@='Teste'")
				.isConditionMet(order(true, null, "Teste", null)));
		assertFalse(subscription("@IsSOTrx@='Y' & @Description@='Teste'")
				.isConditionMet(order(true, null, "Outro", null)));
	}

	@Test
	public void andWithNumericComparison() {
		String cond = "@DocStatus@=CO & @GrandTotal@>1000";
		assertTrue(subscription(cond).isConditionMet(order(true, "CO", null, "1500")));
		assertFalse(subscription(cond).isConditionMet(order(true, "DR", null, "1500")));
		assertFalse(subscription(cond).isConditionMet(order(true, "CO", null, "500")));
	}

	@Test
	public void invalidConditionIsNotDelivered() {
		assertFalse(subscription("@GrandTotal@ >< 1000").isConditionMet(order(true, "CO", null, "1500")));
	}
}
