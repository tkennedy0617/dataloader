package com.bullhorn.dataloader.service.executor;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bullhorn.dataloader.service.api.BullhornAPI;
import com.bullhorn.dataloader.service.api.EntityInstance;
import com.bullhorn.dataloader.service.csv.JsonRow;
import com.bullhorn.dataloader.service.query.AssociationQuery;
import com.bullhorn.dataloader.util.StringConsts;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;

public class JsonService implements Runnable {
    private final String NAME = "name";

    private final LoadingCache<AssociationQuery, Optional<Integer>> associationCache;
    private final Set<EntityInstance> seenFlag;
    private BullhornAPI bhapi;
    private String entity;
    private JsonRow data;

    private final static Log log = LogFactory.getLog(JsonService.class);

    public JsonService(String entity,
                       BullhornAPI bullhornApi,
                       JsonRow data,
                       LoadingCache<AssociationQuery, Optional<Integer>> associationCache,
                       Set<EntityInstance> seenFlag) {
        this.bhapi = bullhornApi;
        this.entity = entity;
        this.data = data;
        this.associationCache = associationCache;
        this.seenFlag = seenFlag;
    }

    @Override
    public void run() {
        String entityBase = bhapi.getRestURL() + StringConsts.ENTITY_SLASH + getEntity();
        String restToken = StringConsts.END_BH_REST_TOKEN + bhapi.getBhRestToken();
        try {
            Map<String, Object> toOneIdentifiers = upsertPreprocessingActions();
            Optional<Integer> optionalEntityId = createOrGetEntity(toOneIdentifiers);
            if (optionalEntityId.isPresent()) {
                updateEntity(entityBase, restToken, optionalEntityId.get());
                saveToMany(optionalEntityId.get(), entity, data.getDeferredActions());
            }
        } catch (IOException | ExecutionException e) {
            log.error(e);
        }
    }

    private Map<String, Object> upsertPreprocessingActions() throws ExecutionException {
        Map<String, Object> preprocessingActions = data.getPreprocessingActions();
        Map<String, Object> toOneIdentifiers = Maps.newHashMap();
        for (String entityName : preprocessingActions.keySet()) {
            AssociationQuery associationQuery = new AssociationQuery(entityName, preprocessingActions.get(entityName));
            addSearchFields(associationQuery, (Map<String, Object>) data.getPreprocessingActions().get(entityName));
            Optional<Integer> toOneId = associationCache.get(associationQuery);
            if (toOneId.isPresent()) {
                Map<String, Integer> toOneAssociation = Maps.newHashMap();
                toOneAssociation.put(StringConsts.ID, toOneId.get());
                toOneIdentifiers.put(entityName, toOneAssociation);
            } else {
                log.error("Failed to upsert to-one association " + associationQuery);
            }
        }
        return toOneIdentifiers;
    }

    private void updateEntity(String entityBase, String restToken, Integer optionalEntityId) throws IOException {
        String postUrl = entityBase + "/" + optionalEntityId + restToken;
        bhapi.saveNonToMany(data.getImmediateActions(), postUrl, "POST");
    }

    private Optional<Integer> createOrGetEntity(Map<String, Object> toOneIdentifiers) throws ExecutionException {
        Object nestJson = mergeObjects(toOneIdentifiers, data.getImmediateActions());
        AssociationQuery associationQuery = new AssociationQuery(getEntity(), nestJson);
        addSearchFields(associationQuery, data.getImmediateActions());
        return associationCache.get(associationQuery);
    }

    private void addSearchFields(AssociationQuery associationQuery, Map<String, Object> actions) {
        ifPresentPut(associationQuery::addInt, StringConsts.ID, actions.get(StringConsts.ID));
        ifPresentPut(associationQuery::addString, NAME, actions.get(NAME));

        String[] propertyFileExistFields = bhapi.getEntityExistsFieldsProperty(entity).split(",");

        for (String propertyFileExistField : propertyFileExistFields) {
            ifPresentPut(associationQuery::addString, propertyFileExistField, actions.get(propertyFileExistField));
        }
    }

    private Map<String, Object> mergeObjects(Map<String, Object> toOneIdentifiers, Map<String, Object> immediateActions) {
        immediateActions.putAll(toOneIdentifiers);
        return immediateActions;
    }

    private void saveToMany(Integer entityId, String entity, Map<String, Object> toManyProperties) throws ExecutionException, IOException {
        EntityInstance parentEntity = new EntityInstance(String.valueOf(entityId), entity);

        for (String toManyKey : toManyProperties.keySet()) {
            AssociationQuery associationQuery = new AssociationQuery(toManyKey, toManyProperties.get(toManyKey));
            Map<String, Object> entityFieldFilters = (Map) toManyProperties.get(toManyKey);
            ifPresentPut(associationQuery::addInt, StringConsts.ID, entityFieldFilters.get(StringConsts.ID));
            ifPresentPut(associationQuery::addString, NAME, entityFieldFilters.get(NAME));
            Optional<Integer> associatedId = associationCache.get(associationQuery);
            if (associatedId.isPresent()) {
                EntityInstance associationEntity = new EntityInstance(String.valueOf(associatedId.get()), associationQuery.getEntity());
                associate(parentEntity, associationEntity);
            }
        }
    }

    private void associate(EntityInstance parentEntity, EntityInstance associationEntity) throws IOException {
        synchronized (seenFlag) {
            if (!seenFlag.contains(parentEntity)) {
                seenFlag.add(parentEntity);
                bhapi.dissociateEverything(parentEntity, associationEntity);
            }
        }
        bhapi.associateEntity(parentEntity, associationEntity);
    }

    private static void ifPresentPut(BiConsumer<String, String> consumer, String fieldName, Object value) {
        if (value != null) {
            consumer.accept(fieldName, value.toString());
        }
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }
}
