"""Generated class for building_translation.json"""


class BuildingTranslation:
  """Generated schema class"""

  def __init__(self):
    self.present_value2 = None
    self.units = None
    self.ref = None
    self.states = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = BuildingTranslation()
    result.present_value2 = source.get('present_value2')
    result.units = source.get('units')
    result.ref = source.get('ref')
    result.states = source.get('states')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = BuildingTranslation.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.present_value2:
      result['present_value2'] = self.present_value2 # 5
    if self.units:
      result['units'] = self.units # 5
    if self.ref:
      result['ref'] = self.ref # 5
    if self.states:
      result['states'] = self.states # 5
    return result
