
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# unittests
LOCAL_MODULE_TAGS := tests
LOCAL_SRC_FILES := $(call all-java-files-under, common, unit)
LOCAL_JAVA_LIBRARIES := android.test.runner
LOCAL_STATIC_JAVA_LIBRARIES := mockito-target ub-uiautomator espresso-core guava
LOCAL_JARJAR_RULES := $(LOCAL_PATH)/jarjar-rules.txt
LOCAL_PACKAGE_NAME := DocumentsUIUnitTests
LOCAL_INSTRUMENTATION_FOR := DocumentsUI
LOCAL_CERTIFICATE := platform

include $(CLEAR_VARS)

# functional tests
LOCAL_MODULE_TAGS := tests
LOCAL_SRC_FILES := $(call all-java-files-under, common, functional)
LOCAL_JAVA_LIBRARIES := android.test.runner
LOCAL_STATIC_JAVA_LIBRARIES := mockito-target ub-uiautomator espresso-core guava
LOCAL_JARJAR_RULES := $(LOCAL_PATH)/jarjar-rules.txt
LOCAL_PACKAGE_NAME := DocumentsUIFunctionalTests
LOCAL_INSTRUMENTATION_FOR := DocumentsUI
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)

