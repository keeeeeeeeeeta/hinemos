/*
 * Copyright (c) 2018 NTT DATA INTELLILINK Corporation. All rights reserved.
 *
 * Hinemos (http://www.hinemos.info/)
 *
 * See the LICENSE file for licensing information.
 */

package com.clustercontrol.maintenance.factory;

import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.clustercontrol.accesscontrol.bean.RoleIdConstant;
import com.clustercontrol.commons.util.HinemosPropertyCommon;
import com.clustercontrol.commons.util.JpaTransactionManager;
import com.clustercontrol.maintenance.util.QueryUtil;
import com.clustercontrol.monitor.run.model.MonitorInfo;
import com.clustercontrol.monitor.session.MonitorSettingControllerBean;

/**
 * バイナリの収集蓄積情報の削除処理.
 *
 * @version 6.1.0
 * @since 6.1.0
 *
 */
public class MaintenanceCollectBinaryData extends MaintenanceObject {

	private static Log m_log = LogFactory.getLog(MaintenanceCollectBinaryData.class);

	private static final Object _deleteLock = new Object();

	private String monitorTypeId = null;

	/**
	 * コンストラクタ.
	 */
	public MaintenanceCollectBinaryData() {
		super();
	}

	/**
	 * コンストラクタ.
	 * 
	 * @param monitorType
	 *            監視種別
	 */
	public MaintenanceCollectBinaryData(String monitorTypeId) {
		this.monitorTypeId = monitorTypeId;
	}

	/**
	 * 削除処理
	 */
	@Override
	protected int _delete(Long boundary, boolean status, String ownerRoleId) {
		int ret = 0;
		JpaTransactionManager jtm = null;

		try {
			//AdminRoleの場合、かつバイナリ監視すべてを対象とする場合は、全て削除
			if(RoleIdConstant.isAdministratorRole(ownerRoleId) && this.monitorTypeId == null){
				jtm = new JpaTransactionManager();
				jtm.begin();
				ret = delete(boundary, status);
				jtm.commit();

			} else {
				ArrayList<MonitorInfo> monitorList = new MonitorSettingControllerBean().getMonitorList();

				ArrayList<String> monitorIdList = new ArrayList<>();

				for (MonitorInfo monitorInfo : monitorList) {
					if (this.monitorTypeId != null 
							&& !this.monitorTypeId.equals(monitorInfo.getMonitorTypeId())) {
						continue;
					}
					if (RoleIdConstant.isAdministratorRole(ownerRoleId) 
							|| monitorInfo.getOwnerRoleId().equals(ownerRoleId)) {
						monitorIdList.add(monitorInfo.getMonitorId());
					}
				}
				if (monitorIdList.isEmpty()) {
					return ret;
				}
				jtm = new JpaTransactionManager();
				jtm.begin();

				// 削除処理.
				for (int i = 0; i < monitorIdList.size(); i++) {
					synchronized (_deleteLock) {
						ret += delete(boundary, status, monitorIdList.get(i));
						jtm.commit();
					}
				}
			}

		} catch (Exception e) {
			String countMessage = "delete count : " + ret + " records" + "\n";
			m_log.warn("deleteCollectData() : " + countMessage
					+ e.getClass().getSimpleName() + ", " + e.getMessage(), e);
			ret = -1;
			if (jtm != null)
				jtm.rollback();
		} finally {
			if (jtm != null)
				jtm.close();
		}
		return ret;
	}

	/**
	 * 削除SQL実行.
	 */
	protected int delete(Long boundary, boolean status, String monitorId) {
		m_log.debug("_delete() start : status = " + status);

		// SQL文の実行
		// for HA (縮退判定時間を延ばすため)、シングルには影響なし(0)：タイムアウト値設定
		int ret = QueryUtil.deleteCollectBinaryDataByDateTimeAndMonitorId(boundary,
				HinemosPropertyCommon.maintenance_query_timeout.getIntegerValue(), monitorId);

		// 終了
		m_log.debug("_delete() count : " + ret);
		return ret;
	}

	protected int delete(Long boundary, boolean status) {
		m_log.debug("_delete() start : status = " + status);

		//SQL文の実行
		//for HA (縮退判定時間を延ばすため)、シングルには影響なし(0)：タイムアウト値設定
		int ret = QueryUtil.deleteCollectBinaryDataByDateTime(boundary,
				HinemosPropertyCommon.maintenance_query_timeout.getIntegerValue());

		//終了
		m_log.debug("_delete() count : " + ret);
		return ret;
	}
}
