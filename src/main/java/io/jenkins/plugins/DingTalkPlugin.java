package io.jenkins.plugins;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import io.jenkins.plugins.DingTalkNotifierConfig.DingTalkNotifierConfigDescriptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import io.jenkins.plugins.enums.BuildStatusEnum;
import io.jenkins.plugins.enums.MsgTypeEnum;
import io.jenkins.plugins.enums.NoticeOccasionEnum;
import io.jenkins.plugins.model.BuildJobModel;
import io.jenkins.plugins.model.ButtonModel;
import io.jenkins.plugins.model.MessageModel;
import io.jenkins.plugins.service.impl.DingTalkServiceImpl;
import io.jenkins.plugins.tools.Logger;
import io.jenkins.plugins.tools.Utils;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * 钉钉通知插件，核心业务逻辑入口
 *
 * @author stanzhai
 */
@Log4j
@ToString
@NoArgsConstructor
public class DingTalkPlugin extends Notifier implements SimpleBuildStep {
  private final DingTalkServiceImpl service = new DingTalkServiceImpl();

  private final String rootPath = Jenkins.get().getRootUrl();
  private ArrayList<DingTalkNotifierConfig> notifierConfigs;
  private DingTalkParamNotify paramNotify;

  /**
   * 在配置页面展示的列表，需要跟 `全局配置` 同步机器人信息
   *
   * @return 机器人配置列表
   */
  public ArrayList<DingTalkNotifierConfig> getNotifierConfigs() {
    ArrayList<DingTalkNotifierConfig> notifierConfigsList = new ArrayList<>();
    ArrayList<DingTalkRobotConfig> robotConfigs = DingTalkGlobalConfig.getInstance()
        .getRobotConfigs();

    for (DingTalkRobotConfig robotConfig : robotConfigs) {
      String id = robotConfig.getId();
      DingTalkNotifierConfig newNotifierConfig = new DingTalkNotifierConfig(robotConfig);

      if (notifierConfigs != null && !notifierConfigs.isEmpty()) {
        for (DingTalkNotifierConfig notifierConfig : notifierConfigs) {
          String robotId = notifierConfig.getRobotId();
          if (id.equals(robotId)) {
            newNotifierConfig.copy(notifierConfig);
          }
        }
      }

      notifierConfigsList.add(newNotifierConfig);
    }

    return notifierConfigsList;
  }

  /**
   * 获取用户设置的通知配置
   *
   * @return 用户设置的通知配置
   */
  public List<DingTalkNotifierConfig> getCheckedNotifierConfigs() {
    ArrayList<DingTalkNotifierConfig> notifierConfigs = this.getNotifierConfigs();

    return notifierConfigs.stream().filter(DingTalkNotifierConfig::isChecked)
        .collect(Collectors.toList());
  }

  public DingTalkParamNotify getParamNotify() {
    return this.paramNotify;
  }

  @DataBoundConstructor
  public DingTalkPlugin(ArrayList<DingTalkNotifierConfig> notifierConfigs, DingTalkParamNotify paramNotify) {
    this.notifierConfigs = notifierConfigs;
    this.paramNotify = paramNotify;
  }

  @Override
  public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull EnvVars env, @NonNull Launcher launcher, @NonNull TaskListener listener) throws InterruptedException, IOException {
    Result result = run.getResult();
    NoticeOccasionEnum noticeOccasion = getNoticeOccasion(result);
    this.send(run, listener, noticeOccasion);
  }

  @Override
  public BuildStepDescriptor getDescriptor() {
    return Jenkins.get().getDescriptorByType(DingTalkPluginDescriptor.class);
  }

  @Extension @Symbol("dingtalkNotifier")
  public static class DingTalkPluginDescriptor extends BuildStepDescriptor<Publisher> {
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    @NonNull
    @Override
    public String getDisplayName() {
      return "DingTalk Notification";
    }

    /**
     * 通知配置页面
     */
    public DingTalkNotifierConfigDescriptor getDingTalkNotifierConfigDescriptor() {
      return Jenkins.get().getDescriptorByType(DingTalkNotifierConfigDescriptor.class);
    }

    /**
     * 默认的配置项列表
     *
     * @return 默认的通知配置列表
     */
    public List<DingTalkNotifierConfig> getDefaultNotifierConfigs() {
      return DingTalkGlobalConfig.getInstance()
              .getRobotConfigs()
              .stream()
              .map(DingTalkNotifierConfig::new)
              .collect(Collectors.toList());
    }
  }

  public void onStarted(Run<?, ?> run, TaskListener listener) {
    DingTalkGlobalConfig globalConfig = DingTalkGlobalConfig.getInstance();
    log(listener, "全局配置信息，%s", Utils.toJson(globalConfig));
    this.send(run, listener, NoticeOccasionEnum.START);
  }

  private NoticeOccasionEnum getNoticeOccasion(Result result) {
    if (Result.SUCCESS.equals(result)) {
      return NoticeOccasionEnum.SUCCESS;
    }
    if (Result.FAILURE.equals(result)) {
      return NoticeOccasionEnum.FAILURE;
    }
    if (Result.ABORTED.equals(result)) {
      return NoticeOccasionEnum.ABORTED;
    }
    if (Result.UNSTABLE.equals(result)) {
      return NoticeOccasionEnum.UNSTABLE;
    }
    if (Result.NOT_BUILT.equals(result)) {
      return NoticeOccasionEnum.NOT_BUILT;
    }
    return null;
  }

  private BuildStatusEnum getBuildStatus(NoticeOccasionEnum noticeOccasion) {
    if (NoticeOccasionEnum.START.equals(noticeOccasion)) {
      return BuildStatusEnum.START;
    }

    if (NoticeOccasionEnum.SUCCESS.equals(noticeOccasion)) {
      return BuildStatusEnum.SUCCESS;
    }

    if (NoticeOccasionEnum.FAILURE.equals(noticeOccasion)) {
      return BuildStatusEnum.FAILURE;
    }
    if (NoticeOccasionEnum.ABORTED.equals(noticeOccasion)) {

      return BuildStatusEnum.ABORTED;
    }
    if (NoticeOccasionEnum.UNSTABLE.equals(noticeOccasion)) {

      return BuildStatusEnum.UNSTABLE;
    }
    if (NoticeOccasionEnum.NOT_BUILT.equals(noticeOccasion)) {

      return BuildStatusEnum.NOT_BUILT;
    }
    return null;
  }

  private void log(TaskListener listener, String formatMsg, Object... args) {
    DingTalkGlobalConfig globalConfig = DingTalkGlobalConfig.getInstance();
    boolean verbose = globalConfig.isVerbose();
    if (verbose) {
      // Logger.line(listener, LineType.START);
      Logger.debug(listener, "钉钉插件：" + formatMsg, args);
      // Logger.line(listener, LineType.END);
    }
  }

  private Map<String, String> getUser(Run<?, ?> run, TaskListener listener) {
    Cause.UserIdCause userIdCause = run.getCause(Cause.UserIdCause.class);
    // 执行人信息
    User user = null;
    String executorName = null;
    String executorMobile = null;
    if (userIdCause != null && userIdCause.getUserId() != null) {
      user = User.getById(userIdCause.getUserId(), false);
    }

    if (user == null) {
      Cause.RemoteCause remoteCause = run.getCause(Cause.RemoteCause.class);
      Cause.UpstreamCause streamCause = run.getCause(Cause.UpstreamCause.class);
      if (remoteCause != null) {
        executorName = "remote " + remoteCause.getAddr();
      } else if (streamCause != null) {
        executorName = "project " + streamCause.getUpstreamProject();
      }
      if (executorName == null) {
        log(listener, "未获取到构建人信息，将尝试从构建信息中模糊匹配。");
        executorName = run.getCauses().stream().map(Cause::getShortDescription)
                .collect(Collectors.joining());
      }
    } else {
      executorName = user.getDisplayName();
      executorMobile = user.getProperty(DingTalkUserProperty.class).getMobile();
      if (executorMobile == null) {
        log(listener, "用户【%s】暂未设置手机号码，请前往 %s 添加。", executorName,
                user.getAbsoluteUrl() + "/configure");
      }
    }
    Map<String, String> result = new HashMap<>(16);
    result.put("name", executorName);
    result.put("mobile", executorMobile);
    return result;
  }

  private EnvVars getEnvVars(Run<?, ?> run, TaskListener listener) {
    EnvVars envVars;
    try {
      envVars = run.getEnvironment(listener);
    } catch (InterruptedException | IOException e) {
      envVars = new EnvVars();
      log.error(e);
      log(listener, "获取环境变量时发生异常，将只使用 jenkins 默认的环境变量。");
      log(listener, ExceptionUtils.getStackTrace(e));
    }
    return envVars;
  }

  private boolean skip(Run<?, ?> run, TaskListener listener, NoticeOccasionEnum noticeOccasion,
                       DingTalkNotifierConfig notifierConfig) {
    if (!selected(run, listener, notifierConfig)) {
      return true;
    }
    String stage = noticeOccasion.name();
    Set<String> noticeOccasions = notifierConfig.getNoticeOccasions();
    if (noticeOccasions.contains(stage)) {
      return false;
    }
    log(listener, "机器人 %s 已跳过 %s 环节", notifierConfig.getRobotName(), stage);
    return true;
  }

  private boolean selected(Run<?, ?> run, TaskListener listener, DingTalkNotifierConfig notifierConfig) {
    if (this.paramNotify == null) {
      return true;
    }
    String parameterName = this.paramNotify.getParameterName();
    if (Util.fixEmptyAndTrim(parameterName) == null) {
      return true;
    }

    EnvVars envVars = getEnvVars(run, listener);
    final String regexp = envVars.get(parameterName);
    if (regexp == null) {
      throw new RuntimeException("parameterName正则表达式为空，参数错误：" + parameterName);
    }

    final Pattern pattern = Pattern.compile(regexp);
    String label = null;
    if (Util.fixEmptyAndTrim(notifierConfig.getLabel()) == null) {
      label = "";
    } else {
      final String rawLabel = notifierConfig.getLabel().trim();
      label = Util.replaceMacro(rawLabel, envVars);
    }
    String robotName = notifierConfig.getRobotName();
    if (pattern.matcher(label).matches()) {
      log(listener, "Notifying to [%s] - Label [%s] matches expression [%s]", robotName, label, pattern.pattern());
      return true;
    } else {
      log(listener, "Skipping to [%s] - Label [%s] does not match expression [%s]", robotName, label, pattern.pattern());
      return false;
    }
  }

  private void send(Run<?, ?> run, TaskListener listener, NoticeOccasionEnum noticeOccasion) {
    Job<?, ?> job = run.getParent();
    DingTalkPlugin property = this;

    // 执行人信息
    Map<String, String> user = getUser(run, listener);
    String executorName = user.get("name");
    String executorMobile = user.get("mobile");

    // 项目信息
    String projectName = job.getFullDisplayName();
    String projectUrl = job.getAbsoluteUrl();

    // 构建信息
    BuildStatusEnum statusType = getBuildStatus(noticeOccasion);
    String jobName = run.getDisplayName();
    String jobUrl = rootPath + run.getUrl();
    String duration = run.getDurationString();
    List<ButtonModel> btns = Utils.createDefaultBtns(jobUrl);
    List<String> result = new ArrayList<>();
    List<DingTalkNotifierConfig> notifierConfigs = property.getCheckedNotifierConfigs();

    // 环境变量
    EnvVars envVars = getEnvVars(run, listener);

    for (DingTalkNotifierConfig item : notifierConfigs) {
      boolean skipped = skip(run, listener, noticeOccasion, item);

      if (skipped) {
        continue;
      }

      String robotId = item.getRobotId();
      String content = item.getContent();
      boolean atAll = item.isAtAll();
      Set<String> atMobiles = item.getAtMobiles();

      if (StringUtils.isNotEmpty(executorMobile)) {
        atMobiles.add(executorMobile);
      }

      String text = BuildJobModel.builder().projectName(projectName).projectUrl(projectUrl)
              .jobName(jobName)
              .jobUrl(jobUrl).statusType(statusType).duration(duration).executorName(executorName)
              .executorMobile(executorMobile).content(envVars.expand(content).replaceAll("\\\\n", "\n"))
              .build()
              .toMarkdown();

      String statusLabel = statusType == null ? "unknown" : statusType.getLabel();

      MessageModel message = MessageModel.builder()
              .type(MsgTypeEnum.ACTION_CARD)
              .atAll(atAll)
              .atMobiles(atMobiles)
              .title(
                      String.format("%s %s", projectName, statusLabel)
              )
              .text(text).btns(btns).build();

      log(listener, "当前机器人信息，%s", Utils.toJson(item));
      log(listener, "发送的消息详情，%s", Utils.toJson(message));

      String msg = service.send(robotId, message);

      if (msg != null) {
        result.add(msg);
      }
    }

    if (!result.isEmpty()) {
      result.forEach(msg -> Logger.error(listener, msg));
    }
  }
}
