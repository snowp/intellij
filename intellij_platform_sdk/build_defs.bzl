"""Convenience methods for plugin_api."""

# The current indirect ij_product mapping (eg. "intellij-latest")
INDIRECT_IJ_PRODUCTS = {
    "intellij-latest": "intellij-2017.1.1",
    "intellij-beta": "intellij-2017.1.1",
    "android-studio-latest": "android-studio-2.3.1.0",
    "android-studio-beta": "android-studio-2.3.1.0",
    "clion-latest": "clion-2017.1.1",
    "clion-beta": "clion-2017.1.1",
}

DIRECT_IJ_PRODUCTS = {
    "intellij-2017.1.1": struct(
        ide="intellij",
        directory="intellij_ce_2017_1_1",
    ),
    "intellij-2016.3.1": struct(
        ide="intellij",
        directory="intellij_ce_2016_3_1",
    ),
    "android-studio-2.3.0.8": struct(
        ide="android-studio",
        directory="android_studio_2_3_0_8",
    ),
    "android-studio-2.3.1.0": struct(
        ide="android-studio",
        directory="android_studio_2_3_1_0",
    ),
    "clion-162.1967.7": struct(
        ide="clion",
        directory="CL_162_1967_7",
    ),
    "clion-2016.3.2": struct(
        ide="clion",
        directory="clion_2016_3_2",
    ),
    "clion-2017.1.1": struct(
        ide="clion",
        directory="clion_2017_1_1",
    ),
}

def select_for_plugin_api(params):
  """Selects for a plugin_api.

  Args:
      params: A dict with ij_product -> value.
              You may only include direct ij_products here,
              not indirects (eg. intellij-latest).
  Returns:
      A select statement on all plugin_apis. Unless you include a "default",
      a non-matched plugin_api will result in an error.

  Example:
    java_library(
      name = "foo",
      srcs = select_for_plugin_api({
          "intellij-2016.3.1": [...my intellij 2016.3 sources ....],
          "intellij-2012.2.4": [...my intellij 2016.2 sources ...],
      }),
    )
  """
  if not params:
    fail("Empty select_for_plugin_api")

  for indirect_ij_product in INDIRECT_IJ_PRODUCTS:
    if indirect_ij_product in params:
      error_message = "".join([
          "Do not select on indirect ij_product %s. " % indirect_ij_product,
          "Instead, select on an exact ij_product."])
      fail(error_message)

  expanded_params = dict(**params)

  # Expand all indirect plugin_apis to point to their
  # corresponding direct plugin_api.
  #
  # {"intellij-2016.3.1": "foo"} ->
  # {"intellij-2016.3.1": "foo", "intellij-latest": "foo"}
  for indirect_ij_product, resolved_plugin_api in INDIRECT_IJ_PRODUCTS.items():
    if resolved_plugin_api in params:
      expanded_params[indirect_ij_product] = params[resolved_plugin_api]

  # Map the shorthand ij_products to full config_setting targets.
  # This makes it more convenient so the user doesn't have to
  # fully specify the path to the plugin_apis
  select_params = dict()
  for ij_product, value in expanded_params.items():
    if ij_product == "default":
      select_params["//conditions:default"] = value
    else:
      select_params["//intellij_platform_sdk:" + ij_product] = value

  return select(select_params)

def select_for_ide(intellij=None, android_studio=None, clion=None, default=[]):
  """Selects for the supported IDEs.

  Args:
      intellij: Files to use for IntelliJ. If None, will use default.
      android_studio: Files to use for Android Studio. If None will use default.
      clion: Files to use for CLion. If None will use default.
      default: Files to use for any IDEs not passed.
  Returns:
      A select statement on all plugin_apis to lists of files, sorted into IDEs.

  Example:
    java_library(
      name = "foo",
      srcs = select_for_ide(
          clion = [":cpp_only_sources"],
          default = [":java_only_sources"],
      ),
    )
  """
  intellij = intellij if intellij != None else default
  android_studio = android_studio if android_studio != None else default
  clion = clion if clion != None else default

  ide_to_value = {
      "intellij" : intellij,
      "android-studio": android_studio,
      "clion": clion,
  }

  # Map (direct ij_product) -> corresponding ide value
  params = dict()
  for ij_product, value in DIRECT_IJ_PRODUCTS.items():
    params[ij_product] = ide_to_value[value.ide]
  params["default"] = default

  return select_for_plugin_api(params)

def _plugin_api_directory(value):
  return "@" + value.directory + "//"

def select_from_plugin_api_directory(intellij, android_studio, clion):
  """Internal convenience method to generate select statement from the IDE's plugin_api directories."""

  ide_to_value = {
      "intellij" : intellij,
      "android-studio": android_studio,
      "clion": clion,
  }

  # Map (direct ij_product) -> corresponding product directory
  params = dict()
  for ij_product, value in DIRECT_IJ_PRODUCTS.items():
    params[ij_product] = [_plugin_api_directory(value) + item for item in ide_to_value[value.ide]]

  # No ij_product == intellij-latest
  params["default"] = params[INDIRECT_IJ_PRODUCTS["intellij-latest"]]

  return select_for_plugin_api(params)
