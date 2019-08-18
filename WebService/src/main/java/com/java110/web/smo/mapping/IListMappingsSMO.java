package com.java110.web.smo.mapping;

import com.java110.common.exception.SMOException;
import com.java110.core.context.IPageData;
import org.springframework.http.ResponseEntity;

/**
 * 编码映射管理服务接口类
 *
 * add by wuxw 2019-06-29
 */
public interface IListMappingsSMO {

    /**
     * 查询编码映射信息
     * @param pd 页面数据封装
     * @return ResponseEntity 对象数据
     * @throws SMOException 业务代码层
     */
    ResponseEntity<String> listMappings(IPageData pd) throws SMOException;
}