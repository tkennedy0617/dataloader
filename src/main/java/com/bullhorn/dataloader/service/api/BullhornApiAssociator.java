package com.bullhorn.dataloader.service.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.common.base.Joiner;

public class BullhornApiAssociator {
    private static final Log log = LogFactory.getLog(BullhornApiAssociator.class);

    private final  BullhornAPI bhapi;

    public BullhornApiAssociator(BullhornAPI bhapi) {
        this.bhapi = bhapi;
    }

    public void dissociateEverything(EntityInstance parentEntity, EntityInstance childEntity) throws IOException {
        String associationUrl = getQueryAssociationUrl(parentEntity, childEntity);
        String associationIds = Joiner.on(',').join(getIds(associationUrl));
        EntityInstance toManyAssociations = new EntityInstance(associationIds, childEntity.getEntityName());
        String toManyUrl = bhapi.getModificationAssociationUrl(parentEntity, toManyAssociations);
        DeleteMethod deleteMethod = new DeleteMethod(toManyUrl);
        log.debug("Dissociating: " + toManyUrl);
        bhapi.delete(deleteMethod);
    }

    public void associate(EntityInstance parentEntity, EntityInstance associationEntity) throws IOException {
        associateEntity(parentEntity, associationEntity);
    }

    public void associateEntity(EntityInstance parentEntity, EntityInstance childEntity) throws IOException {
        String associationUrl = bhapi.getModificationAssociationUrl(parentEntity, childEntity);
        log.debug("Associating " + associationUrl);
        PutMethod putMethod = new PutMethod(associationUrl);
        bhapi.put(putMethod);
    }

    private String getQueryAssociationUrl(EntityInstance parentEntity, EntityInstance childEntity) {
        return bhapi.getRestURL() + "entity/"
                + parentEntity.getEntityName() + "/"
                + parentEntity.getEntityId()
                + "?fields=" + childEntity.getEntityName()
                + "&BhRestToken=" + bhapi.getBhRestToken();
    }

    private List<String> getIds(String url) throws IOException {
        GetMethod getMethod = new GetMethod(url);
        JSONObject response = bhapi.get(getMethod);
        JSONObject data = response.getJSONObject("data");
        JSONArray elements = data.getJSONObject(data.keys().next()).getJSONArray("data");

        List<String> identifiers = new ArrayList<>(elements.length());
        for (int i = 0; i < elements.length(); i++) {
            identifiers.add(String.valueOf(elements.getJSONObject(i).getInt("id")));
        }
        return identifiers;
    }
}
