package com.bullhorn.dataloader.service.query;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.commons.httpclient.methods.PutMethod;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.bullhorn.dataloader.service.api.BullhornAPI;
import com.bullhorn.dataloader.service.api.EntityInstance;
import com.bullhorn.dataloader.service.api.EntityInstanceTest;
import com.bullhorn.dataloader.util.StringConsts;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;

public class AssociationCacheTest {

    private LoadingCache<AssociationQuery, Optional<Integer>> associationCache;
    private BullhornAPI bhapi;
    private EntityInstance awesomeEntity;
    private final String entity = "awesomeEntity";

    @Before
    public void setUp() throws Exception {
        bhapi = Mockito.mock(BullhornAPI.class);
        JSONObject mockSon = Mockito.mock(JSONObject.class);
        awesomeEntity = EntityInstanceTest.getAwesomeEntityInstance();
        this.associationCache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .build(new AssociationCache(bhapi));
        when(bhapi.getRestURL()).thenReturn("http://awesomeurl/");
        when(bhapi.getBhRestToken()).thenReturn("awesomeRestToken");
        when(bhapi.getLabelByName(entity)).thenReturn(Optional.of("awesomeLabel"));
        when(bhapi.serialize(awesomeEntity)).thenReturn("awesomeSerialized");
        when(mockSon.getInt(StringConsts.CHANGED_ENTITY_ID)).thenReturn(42);
        when(bhapi.put(any(PutMethod.class))).thenReturn(mockSon);
    }

    @Test
    public void testLoad() throws Exception {
        AssociationQuery associationQuery = new AssociationQuery("awesomeEntity", awesomeEntity);
        Optional<Integer> thisIsAwesome = associationCache.get(associationQuery);
        assertEquals(thisIsAwesome.get(), Integer.valueOf(42));
    }
}