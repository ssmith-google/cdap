/*
 * Copyright © 2020 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import * as React from 'react';
import { Redirect, Route, Switch } from 'react-router-dom';
import Helmet from 'react-helmet';
import List from 'components/PluginCreator/List';
import Create from 'components/Replicator/Create';
import { getCurrentNamespace } from 'services/NamespaceStore';
import { Theme } from 'services/ThemeHelper';

export const basepath = '/ns/:namespace/plugincreation';

const PluginCreator: React.FC = () => {
  const pageTitle = `${Theme.productName} | Plugin JSON`;

  return (
    <React.Fragment>
      <Helmet title={pageTitle} />
      <Switch>
        <Route exact path={`${basepath}/create`} component={Create} />
        <Route path={basepath} component={List} />
        <Route
          render={() => {
            return <Redirect to={`/ns/${getCurrentNamespace()}/replication`} />;
          }}
        />
      </Switch>
    </React.Fragment>
  );
};

export default PluginCreator;
