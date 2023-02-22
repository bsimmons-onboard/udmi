/**
 * Function suite to handle UDMIS messages using cloud functions.
 */

/**
 * These version markers are used to automatically check that the deployed cloud functions are suitable for any bit
 * of client code. Clients (e.g. sqeuencer) can query this value and check that it's within range. Values
 * indicate the MIN/MAX versions supported, while the client determines what is required.
 */

const FUNCTIONS_VERSION_MIN = 3;
const FUNCTIONS_VERSION_MAX = 3;

// Hacky stuff to work with "maybe have firestore enabled"
const PROJECT_ID = process.env.GCP_PROJECT || process.env.GCLOUD_PROJECT;
const useFirestore = !!process.env.FIREBASE_CONFIG;
if (!process.env.GCLOUD_PROJECT) {
  console.log('Setting GCLOUD_PROJECT to ' + PROJECT_ID);
  process.env.GCLOUD_PROJECT = PROJECT_ID;
}

const version = require('./version');
const functions = require('firebase-functions');
const admin = require('firebase-admin');
const { PubSub } = require('@google-cloud/pubsub');
const pubsub = new PubSub();
const iot = require('@google-cloud/iot');

const REFLECT_REGISTRY = 'UDMS-REFLECT';
const UDMI_VERSION = version.udmi_version;

const EVENT_TYPE = 'event';
const CONFIG_TYPE = 'config';
const STATE_TYPE = 'state';
const QUERY_TYPE = 'query';

const UPDATE_FOLDER = 'update';
const UDMIS_FOLDER = 'udmis';

const ALL_REGIONS = ['us-central1', 'europe-west1', 'asia-east1'];
let registry_regions = null;

console.log(`UDMI version ${UDMI_VERSION}, functions ${FUNCTIONS_VERSION_MIN}:${FUNCTIONS_VERSION_MAX}`);
console.log(`Deployed by ${version.deployed_by} at ${version.deployed_at}`);

if (useFirestore) {
  admin.initializeApp(functions.config().firebase);
} else {
  console.log('No FIREBASE_CONFIG defined');
}
const firestore = useFirestore ? admin.firestore() : null;

const iotClient = new iot.v1.DeviceManagerClient({
  // optional auth parameters.
});

const registry_promise = getRegistryRegions();

function currentTimestamp() {
  return new Date().toJSON();
}

function recordMessage(attributes, message) {
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const subType = attributes.subType || EVENT_TYPE;
  const subFolder = attributes.subFolder || 'unknown';
  const timestamp = (message && message.timestamp) || currentTimestamp();
  const promises = [];

  if (message) {
    message.timestamp = timestamp;
    message.version = message.version || UDMI_VERSION;
  }
  const event_count = message && message.event_count;
  console.log('Message record', registryId, deviceId, subType, subFolder, event_count);

  if (useFirestore) {
    const reg_doc = firestore.collection('registries').doc(registryId);
    promises.push(reg_doc.set({
      'updated': timestamp
    }, { merge: true }));
    const dev_doc = reg_doc.collection('devices').doc(deviceId);
    promises.push(dev_doc.set({
      'updated': timestamp
    }, { merge: true }));
    const config_doc = dev_doc.collection(subType).doc(subFolder);
    if (message) {
      promises.push(config_doc.set(message));
    } else {
      promises.push(config_doc.delete());
    }
  }

  promises.push(sendEnvelope(registryId, deviceId, subType, subFolder, message));

  return promises;
}

function sendEnvelope(registryId, deviceId, subType, subFolder, message, nonce) {
  if (registryId == REFLECT_REGISTRY) {
    console.log('sendEnvelope squash for', registryId);
    return;
  }
  
  console.log('sendEnvelope', registryId, deviceId, subType, subFolder);

  const messageStr = (typeof message === 'string') ? message : JSON.stringify(message);
  const base64 = Buffer.from(messageStr).toString('base64');
  
  envelope = {
    deviceRegistryId: registryId,
    deviceId: deviceId,
    subType: subType,
    subFolder: subFolder,
    payload: base64
  };

  return sendCommand(REFLECT_REGISTRY, registryId, null, envelope, nonce);
}
  
function sendCommand(registryId, deviceId, subFolder, message) {
  const nonce = message && message.debug_config_nonce;
  return sendCommandStr(registryId, deviceId, subFolder, JSON.stringify(message), nonce);
}

function sendCommandStr(registryId, deviceId, subFolder, messageStr, nonce) {
  return registry_promise.then(() => {
    return sendCommandSafe(registryId, deviceId, subFolder, messageStr, nonce);
  });
}

function sendCommandSafe(registryId, deviceId, subFolder, messageStr, nonce) {
  const cloudRegion = registry_regions[registryId];

  const formattedName =
        iotClient.devicePath(PROJECT_ID, cloudRegion, registryId, deviceId);

  console.log('command', subFolder, nonce, formattedName);

  const binaryData = Buffer.from(messageStr);
  const request = {
    name: formattedName,
    subfolder: subFolder,
    binaryData: binaryData
  };

  return iotClient.sendCommandToDevice(request)
    .catch((e) => {
      console.error('Command error', e.details);
    });
}

exports.udmi_target = functions.pubsub.topic('udmi_target').onPublish((event) => {
  const attributes = event.attributes;
  const subType = attributes.subType || EVENT_TYPE;
  const base64 = event.data;
  const msgString = Buffer.from(base64, 'base64').toString();
  const msgObject = JSON.parse(msgString);

  if (subType != EVENT_TYPE) {
    return null;
  }

  promises = recordMessage(attributes, msgObject);

  return Promise.all(promises);
});

function getRegistries(region) {
  console.log('Fetching registries for ' + region);
  const formattedParent = iotClient.locationPath(PROJECT_ID, region);
  return iotClient.listDeviceRegistries({
    parent: formattedParent,
  }).then(result => {
    const registries = result[0];
    console.log('Processing results for ' + region);
    registries.forEach(registry => {
      registry_regions[registry.id] = region;
    });
  });
}

function getRegistryRegions() {
  registry_regions = {};
  promises = [];
  ALL_REGIONS.forEach(region => {
    promises.push(getRegistries(region));
  });
  return Promise.all(promises).then(() => {
    console.log('Fetched ' + Object.keys(registry_regions).length + ' registry regions');
  }).catch(console.error);
}

exports.udmi_reflect = functions.pubsub.topic('udmi_reflect').onPublish((event) => {
  const attributes = event.attributes;
  const base64 = event.data;
  const msgString = Buffer.from(base64, 'base64').toString();
  const msgObject = JSON.parse(msgString);

  if (!attributes.subFolder) {
    return udmi_process_reflector_state(attributes, msgObject);
  }

  console.log('Reflect message', attributes);

  if (attributes.subFolder != 'udmi') {
    console.error('Unexpected subFolder', attributes.subFolder);
    return;
  }

  const envelope = {};
  envelope.projectId = attributes.projectId;
  envelope.deviceRegistryId = msgObject.deviceRegistryId;
  envelope.deviceId = msgObject.deviceId;
  envelope.subFolder = msgObject.subFolder;
  envelope.subType = msgObject.subType;

  const payloadString = Buffer.from(msgObject.payload, 'base64').toString();
  const payload = JSON.parse(payloadString);
  const nonce = payload && payload.debug_config_nonce;

  console.log('Reflect', nonce, envelope.deviceRegistryId, envelope.deviceId, envelope.subType,
              envelope.subFolder);

  return registry_promise.then(() => {
    envelope.cloudRegion = registry_regions[envelope.deviceRegistryId];
    if (!envelope.cloudRegion) {
      console.log('No cloud region found for target registry', envelope.deviceRegistryId);
      return null;
    }
    if (envelope.subType == QUERY_TYPE) {
      return udmi_query_event(envelope, payload);
    }
    const targetFunction = envelope.subType == 'event' ? 'target' : envelope.subType;
    target = 'udmi_' + targetFunction;
    return publishPubsubMessage(target, envelope, payload);
  });
});

function udmi_process_reflector_state(attributes, msgObject) {
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const subContents = Object.assign({}, version);
  subContents.last_state = msgObject.timestamp;
  subContents.functions_min = FUNCTIONS_VERSION_MIN;
  subContents.functions_max = FUNCTIONS_VERSION_MAX;
  const startTime = currentTimestamp();
  return modify_device_config(registryId, deviceId, UDMIS_FOLDER, subContents, startTime);
}

function udmi_query_event(attributes, msgObject) {
  const subFolder = attributes.subFolder;
  if (subFolder != UPDATE_FOLDER) {
    throw 'Unknown query folder ' + subFolder;
  }

  const projectId = attributes.projectId;
  const registryId = attributes.deviceRegistryId;
  const cloudRegion = attributes.cloudRegion;
  const deviceId = attributes.deviceId;

  console.log('formattedName', projectId, cloudRegion, registryId, deviceId);

  const formattedName = iotClient.devicePath(
    projectId,
    cloudRegion,
    registryId,
    deviceId
  );

  console.log('iot query state', formattedName)

  const request = {
    name: formattedName
  };

  const queries = [
    iotClient.getDevice(request),
    iotClient.listDeviceConfigVersions(request)
  ];

  return Promise.all(queries).then(([device, config]) => {
    const stateBinaryData = device[0].state.binaryData;
    const stateString = stateBinaryData.toString();
    const msgObject = JSON.parse(stateString);
    const lastConfig = config[0].deviceConfigs[0];
    const cloudUpdateTime = lastConfig.cloudUpdateTime.seconds;
    const deviceAckTime = lastConfig.deviceAckTime && lastConfig.deviceAckTime.seconds;
    msgObject.configAcked = String(deviceAckTime >= cloudUpdateTime);
    return process_state_update(attributes, msgObject);
  });
}

exports.udmi_state = functions.pubsub.topic('udmi_state').onPublish((event) => {
  const attributes = event.attributes;
  const base64 = event.data;
  const msgString = Buffer.from(base64, 'base64').toString();
  const msgObject = JSON.parse(msgString);

  if (attributes.subFolder) {
    attributes.subType = STATE_TYPE;
    return process_state_block(attributes, msgObject);
  } else {
    return process_state_update(attributes, msgObject);
  }
});

function process_state_update(attributes, msgObject) {
  let promises = [];
  const deviceId = attributes.deviceId;
  const registryId = attributes.deviceRegistryId;

  promises.push(sendEnvelope(registryId, deviceId, STATE_TYPE, UPDATE_FOLDER, msgObject));

  attributes.subFolder = UPDATE_FOLDER;
  attributes.subType = STATE_TYPE;
  promises.push(publishPubsubMessage('udmi_target', attributes, msgObject));

  // Check both potential locations for last_start, can be cleaned-up post release.
  const stateStart = msgObject.system &&
        (msgObject.system.last_start || msgObject.system.operation.last_start);
  stateStart && promises.push(modify_device_config(registryId, deviceId, 'last_start',
                                                   stateStart, currentTimestamp()));

  for (var block in msgObject) {
    let subMsg = msgObject[block];
    if (typeof subMsg === 'object') {
      attributes.subFolder = block;
      subMsg.timestamp = msgObject.timestamp;
      subMsg.version = msgObject.version;
      promises = promises.concat(process_state_block(attributes, subMsg));
    }
  }

  return Promise.all(promises);
};

function process_state_block(attributes, subMsg) {
  console.log('Publishing udmi_target', attributes.subType, attributes.subFolder);
  promises = []
  promises.push(publishPubsubMessage('udmi_target', attributes, subMsg));
  const new_promises = recordMessage(attributes, subMsg);
  promises.push(...new_promises);
  return promises;
}

exports.udmi_config = functions.pubsub.topic('udmi_config').onPublish((event) => {
  const attributes = event.attributes;
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const subFolder = attributes.subFolder || 'unknown';
  const base64 = event.data;
  const now = Date.now();
  const msgString = Buffer.from(base64, 'base64').toString();

  const msgObject = JSON.parse(msgString);
  const nonce = msgObject && msgObject.debug_config_nonce;
  console.log('Config message', registryId, deviceId, subFolder, nonce, msgString);
  if (!msgString) {
    console.warn('Config abort', registryId, deviceId, subFolder, msgString);
    return null;
  }

  attributes.subType = CONFIG_TYPE;

  const promises = recordMessage(attributes, msgObject);
  promises.push(publishPubsubMessage('udmi_target', attributes, msgObject));

  if (useFirestore) {
    console.info('Deferring to firestore trigger for IoT Core modification.');
  } else {
    promises.push(modify_device_config(registryId, deviceId, subFolder, msgObject, currentTimestamp()));
  }

  return Promise.all(promises);
});

function parse_old_config(configStr, resetConfig) {
  let config = {};
  try {
    config = JSON.parse(configStr || "{}");
  } catch(e) {
    if (!resetConfig) {
      console.warn('Previous config parse error without reset, ignoring update');
      return null;
    }
    config = {};
  }

  if (resetConfig) {
    const configLastStart = config.system &&
          (config.system.last_start ||
           (config.system.operation && config.system.operation.last_start));
    console.warn('Resetting config bock', configLastStart);

    // Preserve the original structure of the config message for backwards compatibility.
    if (config.system && config.system.operation) {
      config = {
        system: {
          "operation": {
            last_start: configLastStart
          }
        }
      }
    } else {
      config = {
        system: {
          last_start: configLastStart
        }
      }
    }
  }
  return config;
}

function update_last_start(config, stateStart) {
  const configLastStart = config.system &&
        (config.system.last_start ||
         (config.system.operation && config.system.operation.last_start));
  const stateNonce = Date.now();
  const shouldUpdate = stateStart && (!configLastStart || (stateStart > configLastStart));
  console.log('State update last state/config', stateStart, configLastStart, shouldUpdate, stateNonce);
  // Preserve the existing structure of the config message to maintain backwards compatability.
  if (config.system && config.system.operation) {
    config.system.operation.last_start = stateStart;
  } else {
    config.system.last_start = stateStart;
  }
  if (config.debug_config_nonce) {
    config.debug_config_nonce = stateNonce;
    config.system.debug_config_nonce = stateNonce;
  }
  return shouldUpdate;
}

async function modify_device_config(registryId, deviceId, subFolder, subContents, startTime) {
  const [oldConfig, version] = await get_device_config(registryId, deviceId);
  var newConfig;
  const nonce = subContents && subContents.debug_config_nonce;

  if (subFolder == 'last_start') {
    newConfig = parse_old_config(oldConfig, false);
    if (!newConfig || !update_last_start(newConfig, subContents)) {
      return;
    }
  } else if (subFolder == 'update') {
    console.log('Config replace version', version, startTime, nonce);
    newConfig = subContents;
  } else {
    const resetConfig = subFolder == 'system' && subContents && subContents.extra_field == 'reset_config';
    newConfig = parse_old_config(oldConfig, resetConfig);
    if (newConfig === null) {
      return;
    }

    newConfig.version = UDMI_VERSION;
    newConfig.timestamp = currentTimestamp();

    console.log('Config modify', subFolder, version, startTime, nonce);
    if (subContents) {
      delete subContents.version;
      delete subContents.timestamp;
      newConfig[subFolder] = subContents;
      newConfig.debug_config_nonce = nonce;
    } else {
      if (!newConfig[subFolder]) {
        console.log('Config target already null', subFolder, version, startTime);
        return;
      }
      delete newConfig[subFolder];
    }
  }

  const attributes = {
    projectId: PROJECT_ID,
    cloudRegion: registry_regions[registryId],
    deviceId: deviceId,
    deviceRegistryId: registryId
  };
  return update_device_config(newConfig, attributes, version)
    .then(() => {
      console.log('Config accepted', subFolder, version, startTime, nonce);
    }).catch(e => {
      console.log('Config rejected', subFolder, version, startTime, nonce);
      return modify_device_config(registryId, deviceId, subFolder, subContents, startTime);
    })
}

async function get_device_config(registryId, deviceId) {
  await registry_promise;
  const cloudRegion = registry_regions[registryId];

  const devicePath = iotClient.devicePath(
    PROJECT_ID,
    cloudRegion,
    registryId,
    deviceId
  );

  const [response] = await iotClient.listDeviceConfigVersions({
    name: devicePath,
  });

  const configs = response.deviceConfigs;
  if (configs.length === 0) {
    return null;
  }

  return [configs[0].binaryData.toString('utf8'), configs[0].version];
}

function update_device_config(message, attributes, preVersion) {
  const projectId = attributes.projectId;
  const cloudRegion = attributes.cloudRegion;
  const registryId = attributes.deviceRegistryId;
  const deviceId = attributes.deviceId;
  const version = preVersion || 0;

  console.log('Updating config version', version);

  const extraField = message.system && message.system.extra_field;
  const normalJson = extraField !== 'break_json';
  console.log('Config extra field is ' + extraField + ' ' + normalJson);

  const nonce = message && message.debug_config_nonce;
  const msgString = normalJson ? JSON.stringify(message) :
        '{ broken because extra_field == ' + message.system.extra_field;
  const binaryData = Buffer.from(msgString);

  const formattedName = iotClient.devicePath(
    projectId,
    cloudRegion,
    registryId,
    deviceId
  );
  console.log('iot modify config version', version, formattedName);

  const request = {
    name: formattedName,
    versionToUpdate: version,
    binaryData: binaryData
  };

  return iotClient
    .modifyCloudToDeviceConfig(request)
    .then(() => sendEnvelope(registryId, deviceId, CONFIG_TYPE, UPDATE_FOLDER, msgString, nonce));
}

function consolidate_config(registryId, deviceId, subFolder) {
  const cloudRegion = registry_regions[registryId];
  const reg_doc = firestore.collection('registries').doc(registryId);
  const dev_doc = reg_doc.collection('devices').doc(deviceId);
  const configs = dev_doc.collection(CONFIG_TYPE);

  if (subFolder == UPDATE_FOLDER) {
    return;
  }

  console.log('consolidating config for', registryId, deviceId);

  const new_config = {
    'version': '1'
  };

  const attributes = {
    projectId: PROJECT_ID,
    cloudRegion: cloudRegion,
    deviceId: deviceId,
    deviceRegistryId: registryId
  };
  const timestamps = [];

  return configs.get()
    .then((snapshot) => {
      snapshot.forEach(doc => {
        const docData = doc.data();
        const docStr = JSON.stringify(docData);
        console.log('consolidating config with', registryId, deviceId, doc.id, docStr);
        if (docData.timestamp) {
          timestamps.push(docData.timestamp);
          docData.timestamp = undefined;
        }
        new_config[doc.id] = docData;
      });

      if (timestamps.length > 0) {
        new_config['timestamp'] = timestamps.sort()[timestamps.length - 1];
      } else {
        new_config['timestamp'] = 'unknown';
      }

      return update_device_config(new_config, attributes);
    });
}

exports.udmi_update = functions.firestore
  .document('registries/{registryId}/devices/{deviceId}/config/{subFolder}')
  .onWrite((change, context) => {
    const registryId = context.params.registryId;
    const deviceId = context.params.deviceId;
    const subFolder = context.params.subFolder;
    return registry_promise.then(consolidate_config(registryId, deviceId, subFolder));
  });

function publishPubsubMessage(topicName, attributes, data) {
  const dataStr = JSON.stringify(data);
  const dataBuffer = Buffer.from(dataStr);
  var attr_copy = Object.assign({}, attributes);

  console.log('Message publish', topicName, JSON.stringify(attributes));

  return pubsub
    .topic(topicName)
    .publish(dataBuffer, attr_copy)
    .then(messageId => {
      console.debug(`Message ${messageId} published to ${topicName}.`);
    });
}
