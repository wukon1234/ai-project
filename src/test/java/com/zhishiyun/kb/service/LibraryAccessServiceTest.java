package com.zhishiyun.kb.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.zhishiyun.kb.entity.KbAclEntity;
import com.zhishiyun.kb.entity.KbLibraryEntity;
import com.zhishiyun.kb.entity.SysUserEntity;
import com.zhishiyun.kb.mapper.KbAclMapper;
import com.zhishiyun.kb.mapper.KbLibraryMapper;
import com.zhishiyun.kb.mapper.SysUserMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LibraryAccessServiceTest {

    @Mock
    private KbAclMapper kbAclMapper;
    @Mock
    private KbLibraryMapper kbLibraryMapper;
    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private LibraryAccessService libraryAccessService;

    @Test
    void employee_getsUserDeptAndPublicRead() {
        SysUserEntity user = new SysUserEntity();
        user.setId(10L);
        user.setRoleCode("EMPLOYEE");
        user.setDeptCode("RD");
        when(sysUserMapper.selectById(10L)).thenReturn(user);

        KbAclEntity userAcl = new KbAclEntity();
        userAcl.setUserId(10L);
        userAcl.setLibraryCode("product");

        KbAclEntity deptAcl = new KbAclEntity();
        deptAcl.setDeptCode("RD");
        deptAcl.setLibraryCode("tech");

        when(kbAclMapper.selectList(any())).thenReturn(
                Collections.singletonList(userAcl),
                Collections.singletonList(deptAcl));

        KbLibraryEntity hr = new KbLibraryEntity();
        hr.setCode("hr");
        hr.setPublicRead(1);
        when(kbLibraryMapper.selectList(any())).thenReturn(Collections.singletonList(hr));

        Set<String> codes = libraryAccessService.accessibleLibraryCodes(10L);
        assertTrue(codes.contains("product"));
        assertTrue(codes.contains("tech"));
        assertTrue(codes.contains("hr"));
        assertFalse(codes.contains("support"));
    }

    @Test
    void kbAdmin_getsAllLibraries() {
        SysUserEntity user = new SysUserEntity();
        user.setId(20L);
        user.setRoleCode("KB_ADMIN");
        when(sysUserMapper.selectById(20L)).thenReturn(user);

        KbLibraryEntity a = new KbLibraryEntity();
        a.setCode("product");
        KbLibraryEntity b = new KbLibraryEntity();
        b.setCode("legal");
        when(kbLibraryMapper.selectList(null)).thenReturn(Arrays.asList(a, b));

        Set<String> codes = libraryAccessService.accessibleLibraryCodes(20L);
        assertTrue(codes.contains("product"));
        assertTrue(codes.contains("legal"));
    }
}
