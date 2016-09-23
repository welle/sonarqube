/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.organization.ws;

import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbTester;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.MediaTypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.core.util.Uuids.UUID_EXAMPLE_03;
import static org.sonar.server.organization.ws.OrganizationsWsTestSupport.setParam;

public class DeleteActionTest {
  private static final String SOME_UUID = "uuid";
  private static final String SOME_KEY = "key";
  private static final int HTTP_CODE_NO_CONTENT = 204;
  public static final String ORGANIZATIONS_TABLE = "organizations";

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private DeleteAction underTest = new DeleteAction(new OrganizationsWsSupport(), userSession, dbTester.getDbClient());
  private WsActionTester wsTester = new WsActionTester(underTest);

  @Test
  public void verify_define() {
    WebService.Action action = wsTester.getDef();
    assertThat(action.key()).isEqualTo("delete");
    assertThat(action.isPost()).isTrue();
    assertThat(action.description()).isEqualTo("Delete an organization.<br/>" +
      "The 'uuid' or 'key' must be provided.<br/>" +
      "Require 'Administer System' permission.");
    assertThat(action.isInternal()).isTrue();
    assertThat(action.since()).isEqualTo("6.2");
    assertThat(action.handler()).isEqualTo(underTest);
    assertThat(action.params()).hasSize(2);
    assertThat(action.responseExample()).isNull();

    assertThat(action.param("uuid"))
      .matches(param -> !param.isRequired())
      .matches(param -> UUID_EXAMPLE_03.equals(param.exampleValue()))
      .matches(param -> "Organization uuid".equals(param.description()));
    assertThat(action.param("key"))
      .matches(param -> !param.isRequired())
      .matches(param -> "foo-company".equals(param.exampleValue()))
      .matches(param -> "Organization key".equals(param.description()));
  }

  @Test
  public void request_fails_if_user_does_not_have_SYSTEM_ADMIN_permission() {
    expectInsufficientPrivilegesFE();

    executeUuidRequest(SOME_UUID);
  }

  @Test
  public void request_fails_if_user_does_not_have_SYSTEM_ADMIN_permission_when_key_is_specified() {
    expectInsufficientPrivilegesFE();

    executeKeyRequest(SOME_KEY);
  }

  @Test
  public void request_fails_if_both_uuid_and_key_are_missing() {
    giveUserSystemAdminPermission();

    expectUuidAndKeySetOrMissingIAE();

    executeRequest(null, null);
  }

  @Test
  public void request_fails_if_both_uuid_and_key_are_provided() {
    giveUserSystemAdminPermission();

    expectUuidAndKeySetOrMissingIAE();

    executeRequest(SOME_UUID, SOME_KEY);
  }

  private void expectUuidAndKeySetOrMissingIAE() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Either 'uuid' or 'key' must be provided, not both");
  }

  @Test
  public void request_does_not_fail_if_table_is_empty_when_specifying_a_uuid() {
    giveUserSystemAdminPermission();

    assertThat(executeUuidRequest("another uuid")).isEqualTo(HTTP_CODE_NO_CONTENT);
  }

  @Test
  public void request_does_not_fail_if_table_is_empty_when_specifying_a_key() {
    giveUserSystemAdminPermission();

    assertThat(executeKeyRequest("another key")).isEqualTo(HTTP_CODE_NO_CONTENT);
  }

  @Test
  public void request_does_not_fail_if_row_with_specified_uuid_does_not_exist() {
    giveUserSystemAdminPermission();
    insertOrganization(SOME_UUID);

    assertThat(executeUuidRequest("another uuid")).isEqualTo(HTTP_CODE_NO_CONTENT);
    assertThat(dbTester.countRowsOfTable(ORGANIZATIONS_TABLE)).isEqualTo(1);
  }

  @Test
  public void request_does_not_fail_if_row_with_specified_key_does_not_exist() {
    giveUserSystemAdminPermission();
    insertOrganization(SOME_UUID);

    assertThat(executeKeyRequest("another key")).isEqualTo(HTTP_CODE_NO_CONTENT);
    assertThat(dbTester.countRowsOfTable(ORGANIZATIONS_TABLE)).isEqualTo(1);
  }

  @Test
  public void request_succeeds_and_delete_row_with_specified_existing_uuid() {
    giveUserSystemAdminPermission();
    insertOrganization(SOME_UUID);
    insertOrganization("other uuid");

    assertThat(executeUuidRequest(SOME_UUID)).isEqualTo(HTTP_CODE_NO_CONTENT);
    assertThat(dbTester.countRowsOfTable(ORGANIZATIONS_TABLE)).isEqualTo(1);
  }

  @Test
  public void request_succeeds_and_delete_row_with_specified_existing_key() {
    giveUserSystemAdminPermission();
    String key = insertOrganization(SOME_UUID).getKey();
    insertOrganization("other uuid");

    assertThat(executeKeyRequest(key)).isEqualTo(HTTP_CODE_NO_CONTENT);
    assertThat(dbTester.countRowsOfTable(ORGANIZATIONS_TABLE)).isEqualTo(1);
  }

  private OrganizationDto insertOrganization(String uuid) {
    OrganizationDto dto = new OrganizationDto()
        .setUuid(uuid)
        .setKey(uuid + "_key")
        .setName(uuid + "_name")
        .setDescription(uuid + "_description")
        .setUrl(uuid + "_url")
        .setAvatarUrl(uuid + "_avatar_url")
        .setCreatedAt((long) uuid.hashCode())
        .setUpdatedAt((long) uuid.hashCode());
    dbTester.getDbClient().organizationDao().insert(dbTester.getSession(), dto);
    dbTester.commit();
    return dto;
  }

  private void giveUserSystemAdminPermission() {
    userSession.setGlobalPermissions(GlobalPermissions.SYSTEM_ADMIN);
  }

  private void expectInsufficientPrivilegesFE() {
    expectedException.expect(ForbiddenException.class);
    expectedException.expectMessage("Insufficient privileges");
  }

  private int executeUuidRequest(String uuid) {
    return executeRequest(uuid, null);
  }

  private int executeKeyRequest(String key) {
    return executeRequest(null, key);
  }

  private int executeRequest(@Nullable String uuid, @Nullable String key) {
    TestRequest request = wsTester.newRequest()
      .setMediaType(MediaTypes.PROTOBUF);
    setParam(request, "uuid", uuid);
    setParam(request, "key", key);
    return request.execute().getStatus();
  }

}
