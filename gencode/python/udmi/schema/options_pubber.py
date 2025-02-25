"""Generated class for options_pubber.json"""


class PubberOptions:
  """Generated schema class"""

  def __init__(self):
    self.fixedSampleRate = None
    self.noHardware = None
    self.noConfigAck = None
    self.noPersist = None
    self.noLastStart = None
    self.barfConfig = None
    self.messageTrace = None
    self.extraPoint = None
    self.missingPoint = None
    self.extraField = None
    self.emptyMissing = None
    self.redirectRegistry = None
    self.smokeCheck = None
    self.noPointState = None
    self.disableWriteback = None
    self.noWriteback = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PubberOptions()
    result.fixedSampleRate = source.get('fixedSampleRate')
    result.noHardware = source.get('noHardware')
    result.noConfigAck = source.get('noConfigAck')
    result.noPersist = source.get('noPersist')
    result.noLastStart = source.get('noLastStart')
    result.barfConfig = source.get('barfConfig')
    result.messageTrace = source.get('messageTrace')
    result.extraPoint = source.get('extraPoint')
    result.missingPoint = source.get('missingPoint')
    result.extraField = source.get('extraField')
    result.emptyMissing = source.get('emptyMissing')
    result.redirectRegistry = source.get('redirectRegistry')
    result.smokeCheck = source.get('smokeCheck')
    result.noPointState = source.get('noPointState')
    result.disableWriteback = source.get('disableWriteback')
    result.noWriteback = source.get('noWriteback')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PubberOptions.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.fixedSampleRate:
      result['fixedSampleRate'] = self.fixedSampleRate # 5
    if self.noHardware:
      result['noHardware'] = self.noHardware # 5
    if self.noConfigAck:
      result['noConfigAck'] = self.noConfigAck # 5
    if self.noPersist:
      result['noPersist'] = self.noPersist # 5
    if self.noLastStart:
      result['noLastStart'] = self.noLastStart # 5
    if self.barfConfig:
      result['barfConfig'] = self.barfConfig # 5
    if self.messageTrace:
      result['messageTrace'] = self.messageTrace # 5
    if self.extraPoint:
      result['extraPoint'] = self.extraPoint # 5
    if self.missingPoint:
      result['missingPoint'] = self.missingPoint # 5
    if self.extraField:
      result['extraField'] = self.extraField # 5
    if self.emptyMissing:
      result['emptyMissing'] = self.emptyMissing # 5
    if self.redirectRegistry:
      result['redirectRegistry'] = self.redirectRegistry # 5
    if self.smokeCheck:
      result['smokeCheck'] = self.smokeCheck # 5
    if self.noPointState:
      result['noPointState'] = self.noPointState # 5
    if self.disableWriteback:
      result['disableWriteback'] = self.disableWriteback # 5
    if self.noWriteback:
      result['noWriteback'] = self.noWriteback # 5
    return result
