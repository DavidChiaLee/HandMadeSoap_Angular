import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OilSearchListComponent, OilListConfig } from '../components/oil-search-list.component';

@Component({
  selector: 'app-oils',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, OilSearchListComponent],
  template: `<app-oil-search-list [config]="oilConfig"></app-oil-search-list>`



})
export class OilsComponent implements OnInit {
  readonly oilConfig: OilListConfig = {
    title: '天然油品',
    placeholder: '輸入關鍵字搜尋油品名稱',
    emptyWithKeyword: '查無符合條件的油品',
    emptyWithoutKeyword: '查無油品資料',
    detailField: 'benefits',
    routePrefix: '/oils',
    baseEndpoint: 'oils'
  };

  constructor() {}

  ngOnInit(): void {}
}
