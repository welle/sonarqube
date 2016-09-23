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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.core.util.Uuids;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.MediaTypes;
import org.sonarqube.ws.Organizations;

import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonar.test.JsonAssert.assertJson;

public class SearchActionTest {
  private static final OrganizationDto ORGANIZATION_DTO = new OrganizationDto()
    .setUuid("a uuid")
    .setKey("the_key")
    .setName("the name")
    .setDescription("the description")
    .setUrl("the url")
    .setAvatarUrl("the avatar url")
    .setCreatedAt(1_999_000L)
    .setUpdatedAt(1_888_000L);

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private SearchAction underTest = new SearchAction(dbTester.getDbClient(), new OrganizationsWsSupport());
  private WsActionTester wsTester = new WsActionTester(underTest);

  @Test
  public void verify_define() {
    WebService.Action action = wsTester.getDef();
    assertThat(action.key()).isEqualTo("search");
    assertThat(action.isPost()).isFalse();
    assertThat(action.description()).isEqualTo("Search for organizations");
    assertThat(action.isInternal()).isTrue();
    assertThat(action.since()).isEqualTo("6.2");
    assertThat(action.handler()).isEqualTo(underTest);
    assertThat(action.params()).hasSize(2);
    assertThat(action.responseExample()).isEqualTo(getClass().getResource("example-search.json"));
    WebService.Param pParam = action.param("p");
    assertThat(pParam.isRequired()).isFalse();
    assertThat(pParam.defaultValue()).isEqualTo("1");
    assertThat(pParam.description()).isEqualTo("1-based page number");
    WebService.Param psParam = action.param("ps");
    assertThat(psParam.isRequired()).isFalse();
    assertThat(psParam.defaultValue()).isEqualTo("25");
    assertThat(psParam.description()).isEqualTo("Page size. Must be greater than 0.");
  }

  @Test
  public void verify_response_example() throws URISyntaxException, IOException {
    insertOrganization(new OrganizationDto()
      .setUuid(Uuids.UUID_EXAMPLE_02)
      .setKey("bar-company")
      .setName("Bar Company")
      .setDescription("The Bar company produces quality software too.")
      .setUrl("https://www.bar.com")
      .setAvatarUrl("https://www.bar.com/logo.png")
      .setCreatedAt(1_999_000L)
      .setUpdatedAt(1_888_000L));
    insertOrganization(new OrganizationDto()
      .setUuid(Uuids.UUID_EXAMPLE_01)
      .setKey("foo-company")
      .setName("Foo Company")
      .setDescription("The Foo company produces quality software.")
      .setUrl("https://www.foo.com")
      .setAvatarUrl("https://www.foo.com/foo.png")
      .setCreatedAt(1_999_000L)
      .setUpdatedAt(1_888_000L));

    String response = executeJsonRequest(null, null);

    assertJson(response).isSimilarTo(IOUtils.toString(getClass().getResource("example-search.json")));
  }

  @Test
  public void request_on_empty_db_returns_an_empty_organization_list() {
    assertThat(executeRequest(null, null)).isEmpty();
    assertThat(executeRequest(null, 1)).isEmpty();
    assertThat(executeRequest(1, null)).isEmpty();
    assertThat(executeRequest(1, 10)).isEmpty();
    assertThat(executeRequest(2, null)).isEmpty();
    assertThat(executeRequest(2, 1)).isEmpty();
  }

  @Test
  public void request_returns_empty_on_table_with_single_row_when_not_requesting_the_first_page() {
    insertOrganization(ORGANIZATION_DTO);

    assertThat(executeRequest(2, null)).isEmpty();
    assertThat(executeRequest(2, 1)).isEmpty();
    int somePage = Math.abs(new Random().nextInt(10)) + 2;
    assertThat(executeRequest(somePage, null)).isEmpty();
    assertThat(executeRequest(somePage, 1)).isEmpty();
  }

  @Test
  public void request_returns_rows_ordered_by_createdAt_descending_applying_requested_paging() {
    long time = 1_999_999L;
    insertOrganization(ORGANIZATION_DTO.setUuid("uuid3").setKey("key-3").setCreatedAt(time));
    insertOrganization(ORGANIZATION_DTO.setUuid("uuid1").setKey("key-1").setCreatedAt(time + 1_000));
    insertOrganization(ORGANIZATION_DTO.setUuid("uuid2").setKey("key-2").setCreatedAt(time + 2_000));
    insertOrganization(ORGANIZATION_DTO.setUuid("uuid5").setKey("key-5").setCreatedAt(time + 3_000));
    insertOrganization(ORGANIZATION_DTO.setUuid("uuid4").setKey("key-4").setCreatedAt(time + 5_000));

    assertThat(executeRequest(1, 1))
      .extracting("uuid", "key")
      .containsExactly(tuple("uuid4", "key-4"));
    assertThat(executeRequest(2, 1))
      .extracting("uuid", "key")
      .containsExactly(tuple("uuid5", "key-5"));
    assertThat(executeRequest(3, 1))
      .extracting("uuid", "key")
      .containsExactly(tuple("uuid2", "key-2"));
    assertThat(executeRequest(4, 1))
      .extracting("uuid", "key")
      .containsExactly(tuple("uuid1", "key-1"));
    assertThat(executeRequest(5, 1))
      .extracting("uuid", "key")
      .containsExactly(tuple("uuid3", "key-3"));
    assertThat(executeRequest(6, 1))
      .isEmpty();

    assertThat(executeRequest(1, 5))
      .extracting("uuid")
      .containsExactly("uuid4", "uuid5", "uuid2", "uuid1", "uuid3");
    assertThat(executeRequest(2, 5))
      .isEmpty();
    assertThat(executeRequest(1, 3))
      .extracting("uuid")
      .containsExactly("uuid4", "uuid5", "uuid2");
    assertThat(executeRequest(2, 3))
      .extracting("uuid")
      .containsExactly("uuid1", "uuid3");
  }

  private void insertOrganization(OrganizationDto dto) {
    DbSession dbSession = dbTester.getSession();
    dbTester.getDbClient().organizationDao().insert(dbSession, dto);
    dbSession.commit();
  }

  private List<Organizations.Organization> executeRequest(@Nullable Integer page, @Nullable Integer pageSize) {
    TestRequest request = wsTester.newRequest()
      .setMediaType(MediaTypes.PROTOBUF);
    populateRequest(page, pageSize, request);
    try {
      return Organizations.SearchWsResponse.parseFrom(request.execute().getInputStream()).getOrganizationList();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private String executeJsonRequest(@Nullable Integer page, @Nullable Integer pageSize) {
    TestRequest request = wsTester.newRequest()
      .setMediaType(MediaTypes.JSON);
    populateRequest(page, pageSize, request);
    return request.execute().getInput();
  }

  private void populateRequest(@Nullable Integer page, @Nullable Integer pageSize, TestRequest request) {
    if (page != null) {
      request.setParam("p", valueOf(page));
    }
    if (pageSize != null) {
      request.setParam("ps", valueOf(pageSize));
    }
  }

}
